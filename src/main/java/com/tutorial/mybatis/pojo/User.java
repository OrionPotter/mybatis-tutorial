package com.tutorial.mybatis.pojo;

import lombok.Data;

@Data
public class User {
    private int id;
    private String name;
    private Address address;
}
