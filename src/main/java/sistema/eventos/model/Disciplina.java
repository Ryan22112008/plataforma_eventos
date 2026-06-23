package sistema.eventos.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "disciplinas")
public class Disciplina { // Nome corrigido aqui

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(name = "carga_horaria", nullable = false)
    private Integer cargaHoraria;

    // Relacionamento sênior: Muitas disciplinas pertencem a um Evento
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    @JsonIgnore // Evita loop infinito na hora de transformar o objeto em JSON
    private Evento evento;

    // Construtor Padrão (Obrigatório para o Hibernate/JPA)
    public Disciplina() {
    }

    // Construtor Completo para seus testes e factories
    public Disciplina(Long id, String nome, Integer cargaHoraria, Evento evento) {
        this.id = id;
        this.nome = nome;
        this.cargaHoraria = cargaHoraria;
        this.evento = evento;
    }

    // Getters e Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Integer getCargaHoraria() {
        return cargaHoraria;
    }

    public void setCargaHoraria(Integer cargaHoraria) {
        this.cargaHoraria = cargaHoraria;
    }

    public Evento getEvento() {
        return evento;
    }

    public void setEvento(Evento evento) {
        this.evento = evento;
    }
}