# Scenic Glass TV — pacote CleanYourTV

Este ZIP contém o **APK assinado**, o instalador da Home personalizada e as ferramentas originais
de *debloat* e restauração do CleanYourTV. É uma instalação feita **a partir de um computador**
com acesso ADB à TV; não é um único APK que faz alterações no sistema.

## Antes

1. Extraia **todo o ZIP** numa pasta que você possa guardar. Não execute scripts dentro do ZIP.
2. Ative as opções de desenvolvedor e a depuração ADB na TV e confirme o computador autorizado.
3. Descubra o IP da TV. O computador deve conseguir alcançá-la na rede local.
4. Instale Python 3. No Windows, use PowerShell; no macOS/Linux, use Bash.
5. Ao terminar e validar tudo, desative a depuração ADB na TV e reinicie. Você pode conferir
   se a porta de ADB deixou de responder com `tools/adb-closed.py`.

## Instalação

**macOS, Linux ou Git Bash** (da pasta extraída):

```bash
TV_HOST=192.168.1.50 bash installer/setup.sh
```

**Windows PowerShell** (da pasta extraída):

```powershell
.\installer\setup.ps1 -TvHost 192.168.1.50
```

Substitua o IP pelo da sua TV. O instalador prepara as dependências Python, identifica o modelo,
salva um registro de recuperação, instala o APK pré-compilado, verifica se a Home inicia, aplica
`cmd package compile -m speed`, muda a Home e verifica o resultado. Não exige Android Studio/Gradle.

## Otimizações CleanYourTV

O instalador procura perfis em `examples/*/debloat.txt` que tenham **a mesma fingerprint de firmware**
da TV. Se houver exatamente um, ele oferece a aplicação após instalar a Home; você precisa
confirmar escrevendo `OPTIMIZAR`. Se houver vários perfis compatíveis, examine os comentários
e passe a seleção explicitamente:

```bash
TV_HOST=192.168.1.50 DEBLOAT_LIST=examples/philips-50pus8517/debloat.txt bash installer/setup.sh
```

```powershell
.\installer\setup.ps1 -TvHost 192.168.1.50 -DebloatList examples\philips-50pus8517\debloat.txt
```

Sem **correspondência exata de firmware**, o instalador não desativa aplicativos do fabricante.
Isso é intencional: perfis de outra Philips podem quebrar Chromecast, HDMI, tuner ou Ambilight.
Para criar um novo perfil, siga o `PROMPT.md` e a documentação do repositório CleanYourTV,
testando cada pacote com a TV conectada e um caminho de restauração disponível.

## Recuperação

O estado anterior às mudanças é salvo em `~/.cleanyourtv` e associado ao caminho do perfil.
**Guarde a pasta extraída, inclusive `recovery-baseline.txt`, e não apague esses registros.**
Para restaurar o estado anterior, a partir da pasta extraída, com ADB ainda funcional:

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/MODELO/debloat.txt
TV_HOST=192.168.1.50 tools/restore.sh recovery-baseline.txt
```

No Windows, use `tools\restore.ps1 -TvHost 192.168.1.50 -List <arquivo>`.
Se não houve debloat, use apenas a segunda linha. A restauração não desinstala o APK.

**Importante:** não desinstale a Home em uso antes de restabelecer a Home original. Uma versão
assinada com chave diferente de um APK anterior pode exigir um procedimento de migração;
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` nunca deve disparar desativação do launcher original.
