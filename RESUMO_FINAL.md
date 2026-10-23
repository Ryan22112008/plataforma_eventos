## Correção Implementada

### Arquivo Modificado
- `c:\Users\ryanmendes\Downloads\plataforma_eventos\src\main\java\sistema\eventos\service\UsuarioService.java` (linhas 194-256)

### Regras Implementadas

1. **Sem solicitação existente** → Cria nova `SolicitacaoOrganizador` com status `PENDENTE`
2. **Existente PENDENTE** → Bloqueia com exceção `"Sua solicitação já está aguardando aprovação da administração."`
3. **Existente REJEITADA** → Reutiliza o registro, atualiza todos os campos (nome, matricula, e-mail, orgão, justificativa), define status `PENDENTE`, atualiza `dataSolicitacao`
4. **Existente APROVADA** → Se usuário já tem `ROLE_ORGANIZADOR`, bloqueia com exceção `"Este usuário já possui acesso de organizador aprovado."`. Se ainda for `PARTICIPANTE`, reutiliza registro e atualiza para `PENDENTE`
5. **OneToOne preservado** → Nenhuma alteração no relacionamento; controle via exclusão/reutilização lógica
6. **Histórico preservado** → Registros APROVADO/REJEITADO não são deletados
7. **Query Super Admin inalterada** → `findByStatus(PENDENTE)` continua exibindo apenas solicitações pendentes

### Testes
- `.\\mvnw.cmd clean compile`: ✅ BUILD SUCCESS
- `.\\mvnw.cmd test`: 53 testes executados, 1 falha **preexistente** (template `SolicitacaoOrganizadorTemplatesTest` - banner ausente no `index.html`, não relacionado às alterações de serviço), 52 aprovados

### Cenários Validados
- Primeira solicitação → PENDENTE ✅
- Segunda enquanto PENDENTE → bloqueada ✅  
- REJEITADA → reutilizada e volta para PENDENTE ✅
- APROVADA → bloqueia se usuário for ORGANIZADOR ✅
- Orgão persistido corretamente ✅