[CmdletBinding(DefaultParameterSetName='Local')]
param(
    [Parameter(Mandatory, ParameterSetName='Descartavel')]
    [ValidateRange(33325,65535)][int]$PortaDescartavel,
    [Parameter(Mandatory, ParameterSetName='Descartavel')]
    [string]$PastaInstanciaDescartavel,
    [string]$Mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    [PSCredential]$CredencialAdministrativa,
    [SecureString]$SenhaRepositorioConfianca
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$base = 'finops_apresentacao'
$porta = 33324
$pasta = Join-Path $env:LOCALAPPDATA 'FinOps-Demonstracao'
if ($PSCmdlet.ParameterSetName -eq 'Descartavel') {
    $porta = $PortaDescartavel
    $pasta = (Resolve-Path -LiteralPath $PastaInstanciaDescartavel).ProviderPath
    $temp = (Resolve-Path -LiteralPath $env:TEMP).ProviderPath.TrimEnd('\') + '\'
    if (-not $pasta.StartsWith($temp, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'A instância descartável precisa estar em uma subpasta de TEMP.'
    }
}
$dados = (Resolve-Path -LiteralPath (Join-Path $pasta 'mysql-data')).ProviderPath
$ca = (Resolve-Path -LiteralPath (Join-Path $pasta 'certificado-mysql/ca.pem')).ProviderPath
$confianca = (Resolve-Path -LiteralPath (Join-Path $pasta 'certificado-mysql/confianca.p12')).ProviderPath
$saida = Join-Path $pasta 'mysql-apresentacao-execucao.xml'
$repo = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $repo 'target/orquestrador-finops-0.1.0-SNAPSHOT.jar'
if (-not $env:JAVA_HOME) { throw 'Configure JAVA_HOME com o JDK 25.' }
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
$keytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
foreach ($arquivo in $Mysql, $java, $keytool, $jar) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) { throw "Arquivo necessário ausente: $arquivo" }
}
$Mysql = (Resolve-Path -LiteralPath $Mysql).ProviderPath
$migracoes = @(
    'V001__criar_tabelas.sql',
    'V002__adicionar_autoria_requisicao.sql',
    'V003__criar_usuarios.sql',
    'V004__criar_intervencoes.sql',
    'V005__ampliar_estados_requisicao.sql'
)
foreach ($nome in $migracoes) {
    if (-not (Test-Path -LiteralPath (Join-Path $repo "src/main/resources/db/mysql/$nome") -PathType Leaf)) {
        throw "Migração ausente: $nome"
    }
}
$carga = 'scripts/sql/carregar-catalogo-demo.sql'
if (-not (Test-Path -LiteralPath (Join-Path $repo $carga) -PathType Leaf)) { throw 'Carga do catálogo ausente.' }

if (-not $CredencialAdministrativa) {
    $CredencialAdministrativa = Get-Credential -Message 'Administração do MySQL local'
}
if (-not $CredencialAdministrativa) { throw 'Cadastro cancelado antes de qualquer alteração.' }

function Invoke-Sql([string]$Etapa, [string]$Sql, [switch]$NaBase, [switch]$Sigiloso) {
    $opcoes = @('--no-defaults', '--protocol=TCP', '--host=127.0.0.1', "--port=$porta",
        "--user=$($CredencialAdministrativa.UserName)", '--connect-timeout=5',
        '--ssl-mode=VERIFY_IDENTITY', "--ssl-ca=$ca", '--default-character-set=utf8mb4',
        '--batch', '--skip-column-names', '--raw', "--execute=$Sql")
    if ($NaBase) { $opcoes += "--database=$base" }
    if ($Sigiloso) {
        $resposta = @(& $Mysql @opcoes 2>$null)
    } else {
        $resposta = @(& $Mysql @opcoes)
    }
    if ($LASTEXITCODE -ne 0) { throw "MySQL falhou na etapa '$Etapa' (código $LASTEXITCODE)." }
    return $resposta
}

function New-CredencialMysql([string]$Usuario) {
    $linhas = @(Invoke-Sql "criar conta $Usuario" "CREATE USER '$Usuario'@'127.0.0.1' IDENTIFIED BY RANDOM PASSWORD REQUIRE SSL" -Sigiloso)
    if ($linhas.Count -ne 1) { throw "Resposta inesperada ao criar $Usuario." }
    $campos = $linhas[0].Split([char]9)
    if ($campos.Count -lt 3 -or $campos[0] -cne $Usuario -or $campos[1] -cne '127.0.0.1' -or -not $campos[2]) {
        throw "Resposta inesperada ao criar $Usuario."
    }
    return [PSCredential]::new($Usuario, (ConvertTo-SecureString $campos[2] -AsPlainText -Force))
}

$dep = '10000000-0000-4000-8000-000000000001'
$original = '10000000-0000-4000-8000-000000000002'
$app = 'finops_apresentacao_app'
$cadastro = 'finops_apresentacao_cadastro'
$variaveis = @{}
foreach ($nome in 'MYSQL_PWD', 'FINOPS_DB_URL', 'FINOPS_DB_USER', 'FINOPS_DB_PASSWORD', 'FINOPS_PREPARO_TRUST_PASSWORD') {
    $variaveis[$nome] = [Environment]::GetEnvironmentVariable($nome, 'Process')
}
$criada = $false
Push-Location -LiteralPath $repo
try {
    $env:MYSQL_PWD = $CredencialAdministrativa.GetNetworkCredential().Password
    $identidade = @(Invoke-Sql 'identificar a instância' 'SELECT @@port, @@datadir, VERSION(), DATABASE()')
    if ($identidade.Count -ne 1) { throw 'Não foi possível identificar a instância.' }
    $campos = $identidade[0].Split([char]9)
    if ($campos.Count -ne 4 -or $campos[0] -cne "$porta" -or $campos[2] -notmatch '^8\.\d+\.\d+' -or
        $campos[3] -cne 'NULL' -or
        [IO.Path]::GetFullPath($campos[1]).TrimEnd('\','/') -ine $dados.TrimEnd('\','/')) {
        throw 'Instância, diretório de dados ou versão do MySQL inesperados.'
    }
    if ((Invoke-Sql 'verificar base existente' "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '$base'") -cne '0') {
        throw "$base já existe. A preparação não foi executada e não serve para reiniciar ensaios."
    }
    if ((Invoke-Sql 'verificar contas técnicas existentes' "SELECT COUNT(*) FROM mysql.user WHERE User IN ('$app','$cadastro')") -cne '0' -or
        (Test-Path -LiteralPath $saida)) {
        throw 'Já existe conta técnica ou arquivo de credencial de apresentação. Pare e confira.'
    }
    if ([Console]::IsInputRedirected -or [Console]::IsOutputRedirected -or $Host.Name -ne 'ConsoleHost') {
        throw 'Abra o PowerShell em um terminal interativo. Não redirecione a saída.'
    }
    if (-not $SenhaRepositorioConfianca) {
        $SenhaRepositorioConfianca = Read-Host 'Senha do repositório local de confiança do MySQL (PKCS12)' -AsSecureString
    }
    if (-not $SenhaRepositorioConfianca -or $SenhaRepositorioConfianca.Length -eq 0) { throw 'Senha do PKCS12 não informada.' }
    $env:FINOPS_PREPARO_TRUST_PASSWORD = [Net.NetworkCredential]::new('', $SenhaRepositorioConfianca).Password
    try {
        & $keytool -list -keystore $confianca -storetype PKCS12 -storepass:env FINOPS_PREPARO_TRUST_PASSWORD 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Não foi possível abrir o PKCS12 de confiança. Nenhuma base foi criada.' }
    } finally {
        $env:FINOPS_PREPARO_TRUST_PASSWORD = $null
    }
    Write-Host "Instância confirmada: 127.0.0.1:$porta; criando somente $base."
    Invoke-Sql 'criar base exclusiva' "CREATE DATABASE $base CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci" | Out-Null
    $criada = $true
    if ((Invoke-Sql 'confirmar a base selecionada' 'SELECT DATABASE()' -NaBase) -cne $base) {
        throw 'Base selecionada inesperada.'
    }
    foreach ($nome in $migracoes) {
        Invoke-Sql $nome "SOURCE src/main/resources/db/mysql/$nome" -NaBase | Out-Null
    }
    # Transmite os bytes UTF-8 do nome sem depender da página de código do console Windows.
    $nomeOriginal = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('Recurso de demonstração'))
    Invoke-Sql 'criar dados iniciais' ("START TRANSACTION; " +
        "INSERT INTO departamento (id,nome,teto_mensal,saldo_disponivel) VALUES ('$dep','Engenharia',1000.00,1000.00); " +
        "INSERT INTO recurso_catalogo (id,nome,custo_mensal) VALUES ('$original',CONVERT(FROM_BASE64('$nomeOriginal') USING utf8mb4),12.50); COMMIT;") -NaBase | Out-Null
    Invoke-Sql 'carregar catálogo aprovado' "SOURCE $carga" -NaBase | Out-Null

    $execucao = New-CredencialMysql $app
    $provisionador = New-CredencialMysql $cadastro
    $destino = "'$app'@'127.0.0.1'"
    $permissoes = @(
        "GRANT SELECT ON $base.usuario TO $destino",
        "GRANT SELECT ON $base.recurso_catalogo TO $destino",
        "GRANT SELECT, UPDATE (teto_mensal,saldo_disponivel) ON $base.departamento TO $destino",
        "GRANT SELECT, INSERT, UPDATE (estado,motivo_revisao) ON $base.requisicao TO $destino",
        "GRANT SELECT, INSERT ON $base.item_requisicao TO $destino",
        "GRANT SELECT, INSERT ON $base.intervencao TO $destino",
        "GRANT INSERT ON $base.usuario TO '$cadastro'@'127.0.0.1'"
    )
    Invoke-Sql 'conceder permissões restritas' ($permissoes -join '; ') | Out-Null

    $arquivoConfianca = [uri]::EscapeDataString(([uri]$confianca).AbsoluteUri)
    $senhaConfianca = [uri]::EscapeDataString([Net.NetworkCredential]::new('', $SenhaRepositorioConfianca).Password)
    $env:FINOPS_DB_URL = "jdbc:mysql://127.0.0.1:$porta/$($base)?sslMode=VERIFY_IDENTITY&fallbackToSystemTrustStore=false&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStoreUrl=$arquivoConfianca&trustCertificateKeyStorePassword=$senhaConfianca"
    $env:FINOPS_DB_USER = $cadastro
    $env:FINOPS_DB_PASSWORD = $provisionador.GetNetworkCredential().Password
    $env:MYSQL_PWD = $null
    Write-Host 'Escolha duas senhas diferentes (12 caracteres no mínimo e até 72 bytes UTF-8). O terminal não as exibe.'
    try {
        & $java -jar $jar cadastrar-demonstracao $dep
        if ($LASTEXITCODE -ne 0) { throw "Cadastro interativo falhou (código $LASTEXITCODE)." }
    } finally {
        $env:FINOPS_DB_PASSWORD = $null
        $env:MYSQL_PWD = $CredencialAdministrativa.GetNetworkCredential().Password
        Invoke-Sql 'bloquear a conta técnica de cadastro' "ALTER USER '$cadastro'@'127.0.0.1' ACCOUNT LOCK" | Out-Null
    }

    $custos = @('42.22','84.43','168.86','77.74','161.87','12.79','63.95','44.48')
    $esperado = @("$original;12.50")
    for ($i = 0; $i -lt $custos.Count; $i++) {
        $esperado += '20000000-0000-4000-8000-{0:D12};{1}' -f ($i + 1), $custos[$i]
    }
    $catalogo = @(Invoke-Sql 'conferir catálogo' "SELECT CONCAT(id,';',custo_mensal) FROM recurso_catalogo ORDER BY id" -NaBase)
    $verificacao = @(Invoke-Sql 'conferir dados iniciais' ("SELECT " +
        "(SELECT COUNT(*) FROM departamento), (SELECT COUNT(*) FROM departamento WHERE id='$dep' AND nome='Engenharia' AND teto_mensal=1000.00 AND saldo_disponivel=1000.00), " +
        "(SELECT COUNT(*) FROM usuario), (SELECT COUNT(*) FROM usuario WHERE ativo=1 AND senha_hash LIKE '{bcrypt}%' AND ((login='desenvolvedor' AND perfil='DESENVOLVEDOR' AND departamento_id='$dep') OR (login='arquiteto' AND perfil='ARQUITETO_CLOUD' AND departamento_id IS NULL))), " +
        "(SELECT COUNT(*) FROM requisicao), (SELECT COUNT(*) FROM item_requisicao), (SELECT COUNT(*) FROM intervencao)") -NaBase)
    if (($catalogo -join '|') -cne ($esperado -join '|') -or $verificacao.Count -ne 1 -or
        $verificacao[0] -cne (@(1,1,2,2,0,0,0) -join [char]9)) {
        throw 'O estado final não corresponde aos dados esperados.'
    }

    # Não sobrescreve um arquivo que possa ter surgido desde a verificação inicial.
    $arquivo = [IO.File]::Open($saida, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try {
        $xml = [System.Management.Automation.PSSerializer]::Serialize($execucao)
        $bytes = [Text.Encoding]::UTF8.GetBytes($xml)
        $arquivo.Write($bytes, 0, $bytes.Length)
    } finally {
        $arquivo.Dispose()
    }
    Write-Host 'Conferido: 9 recursos, Engenharia R$ 1.000,00, 2 contas ativas e histórico vazio.'
    Write-Host "Credencial técnica protegida pelo Windows: $saida"
} catch {
    if ($criada) {
        Write-Warning "$base pode estar parcialmente preparada. Não repita, não apague e não inicie o portal nela até verificar a causa."
    }
    throw
} finally {
    foreach ($nome in $variaveis.Keys) {
        [Environment]::SetEnvironmentVariable($nome, $variaveis[$nome], 'Process')
    }
    Pop-Location
}
