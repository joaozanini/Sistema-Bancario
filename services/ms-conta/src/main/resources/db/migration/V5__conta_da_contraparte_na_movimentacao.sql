-- O extrato (R7) identifica origem e destino de uma transferência por conta, CPF e nome.
-- O read model já guardava CPF e nome; faltava o número da conta de cada lado
ALTER TABLE movimentacao_query
    ADD COLUMN conta_origem VARCHAR(50),
    ADD COLUMN conta_destino VARCHAR(50);

-- As transferências já projetadas recebem as contas a partir do evento que as gerou
UPDATE movimentacao_query m
   SET conta_origem = e.payload ->> 'contaOrigem',
       conta_destino = e.payload ->> 'contaDestino'
  FROM eventos_conta e
 WHERE e.id = m.id
   AND m.tipo = 'TRANSFERENCIA';
