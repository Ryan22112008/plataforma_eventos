# Render com Docker e MySQL

O repositório já inclui um `Dockerfile` multi-stage para compilar a aplicação com Java 21 e executar o JAR em uma imagem de runtime menor. O container escuta a variável `PORT` fornecida pelo Render.

1. No Render, crie um **Web Service** conectado ao repositório e selecione **Docker** como runtime. O Render detectará o `Dockerfile` na raiz.
2. Crie ou escolha um serviço MySQL alcançável pelo Web Service. Se hospedar o MySQL no Render, ele deve ser um serviço privado no mesmo workspace; use o hostname interno exibido pelo serviço. Não use `localhost` no Render: isso aponta para o próprio container da aplicação.
3. Em Environment, cadastre `DATABASE_URL` como secret no formato `mysql://USUARIO:SENHA@HOST:PORTA/NOME_DO_BANCO`, usando o host e a porta alcançáveis pelo serviço no Render. Não inclua aspas no valor. Se a senha tiver caracteres especiais, codifique-os para URL.
4. Configure também `REQUIRE_EXTERNAL_DATABASE=true` no ambiente do Web Service. Assim o serviço não inicia usando o H2 local se `DATABASE_URL` estiver ausente ou incorreta.
5. Cadastre `APP_BOOTSTRAP_ADMIN_NAME`, `APP_BOOTSTRAP_ADMIN_EMAIL` e `APP_BOOTSTRAP_ADMIN_PASSWORD` como secrets. A senha inicial precisa ter pelo menos 12 caracteres; essa conta só é criada quando o e-mail ainda não existe no MySQL.
6. Configure `APP_PUBLIC_URL` com a URL HTTPS pública do serviço (por exemplo, `https://seu-servico.onrender.com`) para que os QR codes dos certificados abram a validação no domínio correto.
7. Se as notificações forem necessárias, configure `ADMIN_NOTIFICATION_EMAIL`, `MAIL_USERNAME` e `MAIL_PASSWORD` com endereços e credenciais sob seu controle. Não reutilize credenciais que tenham sido usadas em versões anteriores do projeto.
8. Configure `SESSION_COOKIE_SECURE=true` para que o cookie de sessão só trafegue por HTTPS.
9. Faça o deploy. O processo inicializa o servidor na porta indicada por `PORT`.

Depois de entrar com a conta inicial, a fila de pedidos de acesso de organizador fica em `/admin/solicitacoes`.

Os dados antigos que ficaram no H2 não são copiados automaticamente para o MySQL. Se precisar preservar usuários, cursos e inscrições do H2, faça uma migração antes de apontar o serviço para o novo banco. O H2 permanece como fallback para execução local quando `DATABASE_URL` não está definida.
O console H2 fica desativado por padrão. Não o habilite em produção.

Para executar localmente com o H2 padrão:

```sh
docker build -t eventos-seplag .
docker run --rm -p 8080:8080 -e PORT=8080 -e APP_PUBLIC_URL=http://localhost:8080 -v eventos-data:/app/dados -v eventos-uploads:/app/uploads eventos-seplag
```

Para usar MySQL local, defina `DATABASE_URL=mysql://USUARIO:SENHA@localhost:3308/eventos` no ambiente do processo.
