# Relatório de segurança

**Revisão:** 27/11/2026  
**Escopo:** leitura do código atual, correções locais de baixo risco e análise de vulnerabilidades das dependências Maven resolvidas. Não substitui análise dinâmica.

## Resumo

Não foi confirmado achado crítico. O achado de autorização da API de configuração de certificados está protegido no estado atual: `/api/super-admin/**` exige `ROLE_SUPER_ADMIN` em `SecurityConfig`. A auditoria recebida parece desatualizada nesse ponto.

Foram aplicadas correções locais para limitar tentativas de login, definir uma CSP inicial, reduzir a exposição do objeto de usuário no modelo Thymeleaf, evitar refletir e-mails em mensagens de erro, rejeitar inscrições em eventos em rascunho, ignorar arquivos locais/exportados no Git e manter o console H2 desligado no perfil de produção. As exportações CSV já neutralizam os caracteres de início de fórmula identificados na auditoria. Os dois Dockerfiles já executam como usuário sem privilégios.

## Achados e situação

| # | Gravidade | Situação | Observação |
|---|---|---|---|
| 1 | Alta | Resolvido/verificado | `SecurityConfig` exige `ROLE_SUPER_ADMIN` em `/api/super-admin/**`; o endpoint também fica sob essa regra. Não foi adicionada validação de organizador porque a função é explicitamente administrativa. |
| 2 | Média | Mitigado inicialmente | Após cinco falhas, o identificador de conta recebe bloqueio progressivo de 1, 5, 15 e até 60 minutos. O contador é por processo e é apagado em login bem-sucedido; usar store compartilhado se houver múltiplas instâncias. Bloqueio por conta também pode ser provocado por terceiros e requer monitoramento. |
| 3 | Média | Mitigado no perfil de produção | `application-prod.properties` define cookie `Secure=true`; a configuração do Render deve ativar o perfil `prod` ou definir `SESSION_COOKIE_SECURE=true`. O padrão local permanece apropriado a HTTP de desenvolvimento. |
| 4 | Média | Resolvido/verificado | Exportações de sorteios e quizzes prefixam entradas começando com `=`, `+`, `-`, `@`, TAB ou CR antes de escapá-las como CSV. |
| 5 | Média | Resolvido/verificado | `EmailService` registra o ID interno, não o endereço de e-mail, nos logs de envio. |
| 6 | Média | SCA concluída com OSV; atualizações pendentes | Foram consultadas 141 coordenadas Maven resolvidas; 29 coordenadas retornaram 111 correspondências de avisos OSV. A análise OWASP Dependency-Check não conseguiu buscar a API NVD e foi interrompida; os resultados e limitações estão descritos abaixo. |
| 7 | Média | Resolvido/verificado | `Dockerfile` e `Dockerfile.vercel` criam `appuser`, transferem a posse dos arquivos e executam com `USER appuser`. |
| 8 | Média | Verificado parcialmente | `.env.local` é ignorado e não aparece no histórico Git. Não há evidência de exposição pelo repositório, portanto não alterei credenciais externas. A validade do acesso ao banco não foi testada. |
| 9 | Baixa | Mitigado inicialmente | Foi adicionada CSP com allowlist para recursos usados pelo site, além de `object-src 'none'`, `base-uri 'self'` e `frame-ancestors 'none'`. `unsafe-inline` continua necessário por causa de scripts e handlers inline antigos; removê-lo exige migração posterior. |
| 10 | Baixa | Resolvido | `.gitignore` agora cobre `*.txt`, `*.csv` e `*.xlsx`, que podem conter dados locais ou exportados. |
| 11 | Baixa | Resolvido localmente | `diff-inscritos.txt` foi removido do workspace e a exclusão está pendente de commit. |
| 12 | Baixa | Pendente | `ddl-auto=update` permanece na configuração base. Trocar por `validate` sem migrações pode impedir a inicialização quando o banco divergir do modelo. Planejar Flyway/Liquibase antes da mudança. |
| 13 | Baixa | Mitigado no perfil de produção | `application-prod.properties` mantém o console H2 desativado. A implantação deve ativar o perfil `prod`; `H2_CONSOLE_ENABLED` não deve ser habilitada em produção. |
| 14 | Baixa | Pendente/depende da infraestrutura | A estratégia de cabeçalhos encaminhados depende do proxy confiável à frente da aplicação. Confirmar que o serviço só aceita tráfego pelo proxy esperado. |
| 15 | Baixa | Política a confirmar | Um usuário autenticado que conheça o ID pode abrir evento privado. O fluxo atual exige login e permite solicitar participação; não exige convite individual. Isso é compatível com compartilhar o link privado solicitado para organizadores, mas não equivale a acesso restrito por lista de convidados. |
| 16 | Baixa | Resolvido localmente | Busca por e-mail agora retorna mensagem genérica “Usuário não encontrado”, sem refletir o endereço fornecido. |
| 17 | Baixa | Pendente | O token de check-in continua na URL durante a abertura da página. Uma alteração segura precisa preservar o fluxo QR e evitar que o token apareça em histórico e logs. |
| 18 | Baixa | Pendente | As imagens Docker usam tags mutáveis, sem fixar digest. |
| 19 | Baixa | Resolvido localmente | O advice de avatar agora expõe apenas o nome e a URL da foto ao modelo, em vez da entidade `Usuario` completa. |
| 20 | Baixa | Resolvido localmente | Inscrições em eventos `RASCUNHO` agora são rejeitadas no serviço; eventos privados publicados continuam elegíveis ao fluxo de solicitação. |

## SCA e plano de atualização

O OWASP Dependency-Check 13.0.0 foi iniciado, mas a consulta da base NVD falhou repetidamente no ambiente; não foi possível produzir o relatório NVD. Para não deixar as dependências sem análise, consultei a API em lote OSV para as 141 coordenadas Maven resolvidas. A API OSV consulta vulnerabilidades conhecidas por pacote e versão ([documentação da API](https://google.github.io/osv.dev/post-v1-querybatch/)). O JSON da consulta está em `target/osv-sca-findings.json` (artefato local ignorado pelo Git).

As 111 correspondências não equivalem a 111 vulnerabilidades distintas; são avisos associados a 29 coordenadas, e algumas dependências podem ser apenas de teste. O OSV não substitui o NVD e os avisos precisam ser revisados por severidade e uso efetivo antes de declarar exploração.

Maiores concentrações encontradas:

- Tomcat `10.1.31`: 36 avisos associados; as versões corrigidas dependem do aviso. O BOM de uma atualização do Spring Boot deve alinhar Tomcat e demais componentes.
- Spring Framework `6.1.14`: 13 avisos associados.
- Jackson Databind `2.17.2`: 12 avisos associados.
- Logback Core `1.5.11`: 6 avisos associados.
- Driver PostgreSQL `42.7.4`: 3 avisos; o OSV lista correções até `42.7.12` na linha atual.
- Thymeleaf `3.1.2.RELEASE`: 3 avisos; correções publicadas a partir de `3.1.4.RELEASE`/`3.1.5.RELEASE` conforme o aviso.
- POI OOXML `5.2.5`: 1 aviso, com correção indicada em `5.4.0`.
- Commons Compress `1.25.0`: 2 avisos, com correção indicada em `1.26.0`.

Plano proposto, sem alteração de versões nesta etapa:

1. Preparar uma branch de atualização para Spring Boot `4.2.0`, deixando o BOM alinhar Spring Framework, Security, Tomcat, Jackson e Logback. O release oficial é de 19/11/2026; Java 21 está dentro do suporte, mas Boot 4 requer Servlet 6.1/Tomcat 11, então é uma atualização principal e requer adaptação e regressão completa ([release](https://spring.io/blog/2026/11/19/spring-boot-4-2-0-available-now/), [requisitos](https://docs.spring.io/spring-boot/system-requirements.html)).
2. Atualizar as dependências não gerenciadas pelo BOM conforme os intervalos OSV: POI para pelo menos `5.4.0`, PostgreSQL para `42.7.12`, Commons Compress para `1.26.0` e Thymeleaf para uma versão corrigida compatível. Conferir primeiro se o Boot 4 BOM já fornece versões seguras.
3. Reexecutar OSV e OWASP Dependency-Check com acesso NVD funcional; revisar avisos runtime versus teste e confirmar se cada faixa afetada cobre o código usado.
4. Executar a suíte completa e verificar emissão de PDF, Excel/CSV, banco MySQL/PostgreSQL, autenticação e páginas Thymeleaf antes de integrar/deploy.

## Validações que permaneceram sem achados

- Consultas JPA verificadas usam parâmetros; não foi identificada concatenação de entrada em SQL nativo.
- Os templates usam `th:text`; não foi encontrado `th:utext`.
- CSRF está habilitado; não foram encontrados `@CrossOrigin` nem liberação ampla de CORS.
- Senhas são codificadas com BCrypt e o cadastro valida comprimento mínimo.
- O console H2 fica desativado por padrão.
- Uploads passam por validação e armazenamento com nomes gerados pela aplicação.

## Pendências operacionais

1. Confirmar no Render `SPRING_PROFILES_ACTIVE=prod` ou `SESSION_COOKIE_SECURE=true` e `H2_CONSOLE_ENABLED=false`.
2. Confirmar com o provedor do banco se a credencial local ainda funciona; não há evidência de exposição no Git.
3. Reexecutar OWASP Dependency-Check com a API NVD acessível e validar os 29 coordenadas com correspondências OSV.
4. Planejar migrações antes de substituir `ddl-auto=update` em produção.
5. Avaliar um store compartilhado para bloqueios se a aplicação for executada em múltiplas instâncias.

## Verificação desta revisão

A compilação Maven (`mvnw -q -DskipTests compile`) foi concluída com sucesso. A suíte de testes não foi executada nesta etapa.
