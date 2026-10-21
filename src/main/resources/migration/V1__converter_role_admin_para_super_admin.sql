-- Migration: Converter perfil ROLE_ADMIN para ROLE_SUPER_ADMIN
-- Apenas a(s) conta(s) com perfil ROLE_ADMIN será(ão) alterada(s).
-- Nome, e-mail, senha, status ativo e demais dados são preservados integralmente.

UPDATE tb_usuario
SET perfil = 'ROLE_SUPER_ADMIN'
WHERE perfil = 'ROLE_ADMIN';
