package com.example.backend.Model.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "user_locations")
public class UserLocation {

    @Id
    private Integer userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "zone_name")
    private String zoneName;

    @Column(name = "base_latitude")
    private Double baseLatitude;

    @Column(name = "base_longitude")
    private Double baseLongitude;

    @Column(name = "safe_radius")
    private Double safeRadius = 500.0;
}