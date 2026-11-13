-- O contrato da API usa APROVADA / NAO_APROVADA; as solicitações gravadas antes ficam na mesma grafia
UPDATE solicitacao_cadastro SET status = 'APROVADA' WHERE status = 'APROVADO';
UPDATE solicitacao_cadastro SET status = 'NAO_APROVADA' WHERE status = 'NAO_APROVADO';

-- CEP só com dígitos (o formulário enviava com a máscara 80000-000)
UPDATE solicitacao_cadastro SET cep = regexp_replace(cep, '\D', '', 'g') WHERE cep ~ '\D';
UPDATE cliente SET cep = regexp_replace(cep, '\D', '', 'g') WHERE cep ~ '\D';
