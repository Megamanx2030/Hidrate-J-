# Hidrate Já

Aplicativo Android de lembrete para beber água, feito pensando em pessoas
idosas: telas grandes, texto direto e um aviso difícil de ignorar.

## O que ele faz

- Lembretes em horários fixos, com recorrência diária automática.
- No horário, toca um som suave e vibra, mostra uma notificação na barra e
  abre um aviso em tela cheia (a "tela azul") para confirmar com um toque.
- Três modos de aviso: **som e vibração**, **somente vibrar** ou
  **silencioso** (apenas a notificação na barra).
- Registro do consumo com meta diária e mensal, histórico e relatório.
- Pela própria notificação dá para confirmar que bebeu ou ignorar o lembrete.

## Tecnologias

- **Kotlin** e **Jetpack Compose** (Material 3)
- **Room** para o banco local
- **AlarmManager** com `setAlarmClock` para os alarmes exatos, e um
  *foreground service* para tocar o som sem o processo ser morto

`minSdk` 24, `targetSdk` 36.

## Como compilar

O projeto precisa do JDK que vem com o Android Studio. Se o `JAVA_HOME` não
estiver configurado, aponte para o `jbr`:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

Depois:

```powershell
.\gradlew assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

Para compilar e instalar direto no aparelho conectado:

```powershell
.\gradlew installDebug
```

## Permissões

O app não acessa a internet. As permissões que ele pede existem só para o
alarme funcionar na hora certa: vibração, wake lock, notificações,
alarme exato, aviso em tela cheia, *foreground service*, início após o boot e
isenção de otimização de bateria.
