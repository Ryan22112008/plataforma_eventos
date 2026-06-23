package sistema.eventos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class DisciplinaDTO {
        @NotBlank(message = "O nome da disciplina é obrigatório.")
        @Size(max = 200, message = "O conteúdo da disciplina não pode passar de 200 caracteres.") // <-- ADICIONAR ESTA LINHA
        private String nome;
        private Integer cargaHoraria;

        // Getters e Setters
        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        public Integer getCargaHoraria() { return cargaHoraria; }
        public void setCargaHoraria(Integer cargaHoraria) { this.cargaHoraria = cargaHoraria; }
    }

