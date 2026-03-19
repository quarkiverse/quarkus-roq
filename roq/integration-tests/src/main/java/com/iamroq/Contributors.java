package com.iamroq;

import jakarta.persistence.Entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
public class Contributors extends PanacheEntity {

    protected Contributors() {
    }

    public Contributors(String name) {
        this.name = name;
    }

    String name;

    public String getName() {
        return name;
    }
}
