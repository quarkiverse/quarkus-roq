package com.iamroq;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Named;

@ApplicationScoped
@Named("database")
public class Database {

    @ActivateRequestContext
    public List<String> contributors() {
        return Contributors.listAll().stream().map(c -> {
            Contributors contributor = (Contributors) c;
            return contributor.getName();
        }).toList();
    }

}
