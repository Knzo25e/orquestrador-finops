-- Carga opcional para a base de demonstração. Aplique V001–V005 antes de executar.
-- Estimativas fixas em BRL/mês. Consulte docs/executar-mvp-local.md.
START TRANSACTION;

INSERT INTO recurso_catalogo (id, nome, custo_mensal)
WITH novos (id, nome, custo_mensal) AS (
    SELECT '20000000-0000-4000-8000-000000000001', 'Amazon EC2 t3.micro — Linux, 2 vCPU, 1 GiB', 42.22
    UNION ALL
    SELECT '20000000-0000-4000-8000-000000000002', 'Amazon EC2 t3.small — Linux, 2 vCPU, 2 GiB', 84.43
    UNION ALL
    SELECT '20000000-0000-4000-8000-000000000003', 'Amazon EC2 t3.medium — Linux, 2 vCPU, 4 GiB', 168.86
    UNION ALL
    SELECT '20000000-0000-4000-8000-000000000004', 'Amazon RDS db.t4g.micro — MySQL 8.4, Single-AZ, 2 vCPU, 1 GiB RAM, 20 GiB gp3', 77.74
    UNION ALL
    SELECT '20000000-0000-4000-8000-000000000005', 'Amazon RDS db.t4g.small — MySQL 8.4, Single-AZ, 2 vCPU, 2 GiB RAM, 50 GiB gp3', 161.87
    UNION ALL
    SELECT '20000000-0000-4000-8000-000000000006', 'Amazon S3 Standard — 100 GB', 12.79
    UNION ALL
    SELECT '20000000-0000-4000-8000-000000000007', 'Amazon S3 Standard — 500 GB', 63.95
    UNION ALL
    SELECT '20000000-0000-4000-8000-000000000008', 'Amazon EBS gp3 — 100 GiB, 3.000 IOPS, 125 MiB/s', 44.48
)
-- Ignora apenas UUID, nome e preço idênticos. Um UUID divergente causa erro
-- de chave primária e reverte todo o INSERT no InnoDB.
SELECT novos.id, novos.nome, novos.custo_mensal
FROM novos
WHERE NOT EXISTS (
    SELECT 1
    FROM recurso_catalogo AS existente
    WHERE existente.id = novos.id
      AND CAST(existente.nome AS BINARY) = CAST(novos.nome AS BINARY)
      AND existente.custo_mensal = novos.custo_mensal
);

COMMIT;
