package org.example.entities.parts;

import jakarta.persistence.*;

@MappedSuperclass
public abstract class Part implements StatSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;
    @Column(nullable = false)
    private int weight;
    private String modelPath;
    @Column(nullable = false)
    private boolean active = true;

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public int getWeight() { return weight; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getModelPath() { return modelPath; }

    public void setName(String name) { this.name = name; }
    public void setWeight(int weight) { this.weight = weight; }
    public void setModelPath(String modelPath) { this.modelPath = modelPath; }
}