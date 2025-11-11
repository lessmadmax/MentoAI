package com.mentoai.mentoai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_dates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "date_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id")
    private ActivityEntity activity;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_type", nullable = false, length = 50)
    private DateType dateType;

    @Column(name = "date_value", nullable = false)
    private LocalDateTime dateValue;

    public enum DateType {
        APPLY_START, APPLY_END, EVENT_START, EVENT_END
    }
}


