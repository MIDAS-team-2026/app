"""Bounded single-worker analysis reuse; no clinical scores are fabricated here."""
from collections import OrderedDict
from copy import deepcopy
from hashlib import sha256
import json
import logging
from threading import Lock
from time import monotonic
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from recall.record_execution import record_execution
from recall.recall_score_calculator import is_valid_score

logger = logging.getLogger(__name__)

def audio_cache_identity(path):
    """Ignore rotating S3 credentials, not object/version or content parameters."""
    if not isinstance(path, str):
        return path
    try:
        url = urlsplit(path)
        host = url.hostname or ""
        if url.scheme not in {"http", "https"} or not host.endswith((".amazonaws.com", ".amazonaws.com.cn")):
            return path
        query = parse_qsl(url.query, keep_blank_values=True)
        keys = {key.lower() for key, _ in query}
        if not ({"x-amz-signature", "x-amz-credential", "x-amz-date"} <= keys
                or {"awsaccesskeyid", "signature", "expires"} <= keys):
            return path
        auth_keys = {"x-amz-algorithm", "x-amz-credential", "x-amz-date", "x-amz-expires",
                     "x-amz-signedheaders", "x-amz-signature", "x-amz-security-token",
                     "awsaccesskeyid", "signature", "expires"}
        query = [(key, value) for key, value in query if key.lower() not in auth_keys]
        return urlunsplit((url.scheme, url.netloc, url.path, urlencode(query), url.fragment))
    except ValueError:
        return path


class TurnAnalysisCache:
    def __init__(self, capacity=256, failure_ttl=30, clock=monotonic):
        self.capacity = capacity
        self.failure_ttl = failure_ttl
        self.clock = clock
        self.entries = OrderedDict()
        self.lock = Lock()

    def get_or_analyze(self, session_id, record, analyze, *, retry_failed=False, duration_sec=0.0):
        # Include input content: corrections or a replaced recording invalidate reuse.
        inputs = {key: record.get(key) for key in (
            'transcriptText', 'audioFilePath', 'recordedAt')}
        # Uploads have unique object keys; refreshed access URLs are not new audio.
        inputs['audioFilePath'] = audio_cache_identity(inputs['audioFilePath'])
        digest = sha256(json.dumps(inputs, sort_keys=True, ensure_ascii=False,
                                   default=str).encode('utf-8')).hexdigest()
        key = (session_id, int(record['recordId']))
        with record_execution(('audio', *key)):
            with self.lock:
                cached = self.entries.get(key)
                if cached and cached[0] == digest:
                    _, created, value = cached
                    valid = valid_audio_result(value)
                    if valid or (not retry_failed and self.clock() - created < self.failure_ttl):
                        self.entries.move_to_end(key)
                        logger.info("audio_analysis_cache status=hit sessionId=%s recordId=%s", *key)
                        return deepcopy(value)
            logger.info("audio_analysis_cache status=miss sessionId=%s recordId=%s", *key)
            result = analyze(
                record_id=key[1], session_id=session_id,
                audio_path=record.get('audioFilePath') or '',
                audio_url=record.get('audioFilePath') or '',
                transcript_text=record.get('transcriptText') or '', duration_sec=duration_sec,
            )
            with self.lock:
                self.entries[key] = (digest, self.clock(), deepcopy(result))
                self.entries.move_to_end(key)
                while len(self.entries) > self.capacity:
                    self.entries.popitem(last=False)
            return result


def valid_audio_result(result):
    return (result.get('audio_analysis_available') is True
            and is_valid_score(result.get('speechRiskScore')))


def speech_evaluation(session_id, results, summarize):
    valid = [result for result in results if valid_audio_result(result)]
    if not valid:
        return None, 'NOT_EVALUABLE'
    score = summarize(session_id=session_id, turn_results=valid).get('speechRiskScore')
    if not is_valid_score(score):
        return None, 'NOT_EVALUABLE'
    return score, 'SCORED' if len(valid) == len(results) else 'PARTIAL'


turn_analysis_cache = TurnAnalysisCache()
