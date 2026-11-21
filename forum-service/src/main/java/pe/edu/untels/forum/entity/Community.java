package pe.edu.untels.forum.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "communities", schema = "public")
public class Community {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    public Long getId() { return id; }
    public String getName() { return name; }
}
