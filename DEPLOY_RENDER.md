# Render com Docker

O repositório já inclui um `Dockerfile` multi-stage para compilar a aplicação com Java 21 e executar o JAR em uma imagem de runtime menor. O container escuta a variável `PORT` fornecida pelo Render.

1. No Render, crie um **Web Service** conectado ao repositório e selecione **Docker** como runtime. O Render detectará o `Dockerfile` na raiz.
2. Para preservar o banco H2 e os arquivos enviados entre reinicializações e novos deploys, adicione um Persistent Disk montado em `/var/data`.
3. Configure `DATA_DIR=/var/data` e `UPLOAD_DIR=/var/data/uploads`. Artes de certificados e banners aceitam somente imagens PNG/JPEG reprocessadas pelo servidor.
4. Configure `DB_PASSWORD` com uma senha forte como secret antes da primeira inicialização do banco persistente. Se já existir um banco H2 no disco, preserve a senha com que ele foi criado ou faça uma migração controlada antes de alterá-la.
5. Cadastre `APP_BOOTSTRAP_ADMIN_NAME`, `APP_BOOTSTRAP_ADMIN_EMAIL` e `APP_BOOTSTRAP_ADMIN_PASSWORD` como secrets. A senha inicial precisa ter pelo menos 12 caracteres; essa conta só é criada quando o e-mail ainda não existe.
6. Configure `APP_PUBLIC_URL` com a URL HTTPS pública do serviço (por exemplo, `https://seu-servico.onrender.com`) para que os QR codes dos certificados abram a validação no domínio correto.
7. Se as notificações forem necessárias, configure `ADMIN_NOTIFICATION_EMAIL`, `MAIL_USERNAME` e `MAIL_PASSWORD` com endereços e credenciais sob seu controle. Não reutilize credenciais que tenham sido usadas em versões anteriores do projeto.
8. Configure `SESSION_COOKIE_SECURE=true` para que o cookie de sessão só trafegue por HTTPS.
9. Faça o deploy. O processo inicializa o servidor na porta indicada por `PORT`.

Depois de entrar com a conta inicial, a fila de pedidos de acesso de organizador fica em `/admin/solicitacoes`.

Sem um Persistent Disk, o sistema de arquivos do serviço pode ser descartável: dados do H2 e arquivos enviados não devem ser considerados persistentes.
O console H2 fica desativado por padrão. Não o habilite em produção. Persistent Disks exigem um serviço pago no Render; o plano gratuito não oferece persistência local.

Para executar localmente:

```sh
docker build -t eventos-seplag .
docker run --rm -p 8080:8080 -e PORT=8080 -e APP_PUBLIC_URL=http://localhost:8080 -v eventos-data:/app/dados -v eventos-uploads:/app/uploads eventos-seplag
```
