package org.example.entities;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "app_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Beyblade> beybladeConfigurations = new ArrayList<>();


    public List<Beyblade> removeBeyblade(Beyblade beyblade) {
        if(beybladeConfigurations.contains(beyblade)) {
            beybladeConfigurations.remove(beyblade);
            beyblade.setOwner(null);
        }
        return beybladeConfigurations;

    }

    public List<Beyblade> addBeyblade(Beyblade beyblade) {
        if(!beybladeConfigurations.contains(beyblade)){
            beybladeConfigurations.add(beyblade);
            beyblade.setOwner(this);
        }
        return beybladeConfigurations;
    }
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<Beyblade> getBeybladeConfigurations() {
        return beybladeConfigurations;
    }


    public Long getId() {
        return id;
    }

}
