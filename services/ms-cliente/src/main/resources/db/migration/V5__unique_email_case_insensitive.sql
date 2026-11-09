-- R1: o e-mail é o login e não diferencia maiúsculas. O serviço já compara ignorando caixa,
-- mas só o índice impede que dois autocadastros simultâneos ("Ana@x.com" e "ana@x.com") passem juntos
CREATE UNIQUE INDEX IF NOT EXISTS uk_solicitacao_email_lower ON solicitacao_cadastro (LOWER(email));
CREATE UNIQUE INDEX IF NOT EXISTS uk_cliente_email_lower ON cliente (LOWER(email));
