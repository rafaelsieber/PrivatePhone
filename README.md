# PrivatePhone

App Android minimalista que **só deixa passar ligações de quem está na sua agenda**. Todo o resto é recusado antes de o telefone tocar — sem toque, sem vibração, sem notificação.

Zero dependências externas, zero acesso à internet. Permissões: `READ_CONTACTS` (para saber quem é conhecido), `POST_NOTIFICATIONS` (para avisar se o filtro cair) e `RECEIVE_BOOT_COMPLETED` (para conferir o filtro no boot).

## Como funciona

Ele **não substitui o discador**. Usa a API dedicada do Android para isso, o [`CallScreeningService`](https://developer.android.com/reference/android/telecom/CallScreeningService): o sistema consulta o app antes de a chamada tocar, e o app responde se ela passa ou não.

Você continua usando seu app de telefone normal para ligar. Este só filtra o que chega.

Para um número desconhecido a resposta é `setRejectCall(true)` — a chamada é derrubada na operadora, não apenas silenciada no aparelho.

### Decisões de projeto

- **Número oculto/privado é bloqueado.** Não está na agenda, não passa.
- **Sem permissão de contatos, libera tudo — mas avisa.** É proposital: se a permissão for revogada, o app não tem como saber quem é conhecido. Melhor deixar um desconhecido passar do que bloquear a família inteira por causa de uma permissão que sumiu. Nesse caso, e se o app perder o papel de filtro, fica uma notificação persistente "Filtro de chamadas desligado". A checagem roda ao abrir o app, a cada chamada, no boot e uma vez por dia (JobScheduler).
- **Resposta em milissegundos.** O Android dá uns 5 s para a triagem; passou disso, a chamada toca mesmo que a resposta seja recusar. A agenda fica num índice em memória, carregado quando o processo sobe e relido quando os contatos mudam (`ContentObserver`). Só enquanto a primeira carga não termina a triagem consulta o provedor direto.
- **Casamento por E.164, tolerante ao 9º dígito.** Compara o número inteiro em E.164 (`NORMALIZED_NUMBER`). De reserva, compara DDD + os 8 últimos dígitos, ignorando o nono dígito: um contato salvo como `(11) 9999-8888` casa com `+55 11 99999-8888`. Contato salvo sem DDD não casa pela reserva.
- **Exclusão do auto-revoke.** O Android 11+ tira as permissões de apps que ficam meses sem ser abertos — e este, funcionando bem, nunca é aberto. A tela pede a exclusão enquanto ela não estiver dada.
- **Log.** Cada chamada filtrada vira uma linha no logcat (tag `PrivatePhone`) com a decisão, o motivo e o tempo de resposta em ms.
- **Nada sai do aparelho.** Preferências e histórico ficam em `SharedPreferences`, em storage device-protected — a triagem pode acontecer antes do primeiro desbloqueio do dia.

## Instalação

1. Instale o APK (libere "fontes desconhecidas" quando o Android pedir).
2. Abra o app → **Permitir acesso aos contatos**.
3. **Ativar filtro de chamadas** → aceite o diálogo do Android que torna o app o filtro padrão.
4. O status vira **Proteção ativa**, em verde.

Requer **Android 10** (API 29) ou superior — é a versão em que o papel de filtro de chamadas passou a existir sem precisar substituir o discador.

### Ressalvas

- Xiaomi/Samsung às vezes escondem o papel de filtro de chamadas. Se o diálogo do passo 3 não aparecer, o botão leva aos ajustes do app para escolher na mão.
- Só um app por vez pode filtrar chamadas. Se você usa Truecaller ou o filtro do Google Phone, ele perde esse papel.

## Na tela

- Status da proteção, com o que falta para ela ficar de pé.
- Pausar o bloqueio sem desinstalar.
- Esconder (ou não) os bloqueios do registro de chamadas.
- As últimas 100 chamadas filtradas, para conferir se nada importante foi barrado.

## Build

```bash
cp keystore.properties.example keystore.properties   # e ajuste as senhas
./gradlew :app:assembleRelease
```

O APK sai em `app/build/outputs/apk/release/`. Sem o `keystore.properties`, o build funciona igual mas gera um APK sem assinatura.

Precisa de JDK 17 e do Android SDK (platform 34, build-tools 35.0.0). O caminho do SDK vai em `local.properties`:

```properties
sdk.dir=/caminho/para/Android/Sdk
```

## Código

Em `app/src/main/java/com/rafael/privatephone/`:

| Arquivo | Papel |
|---|---|
| `ContactScreeningService.java` | Decide passa/bloqueia a cada chamada |
| `ContactIndex.java` | Índice da agenda em memória, relido por `ContentObserver` |
| `Contacts.java` | Normalização de números e consulta direta à agenda |
| `Guard.java`, `GuardJob.java`, `BootReceiver.java` | Notificação de filtro desligado e suas checagens |
| `App.java` | Começa a carregar a agenda assim que o processo sobe |
| `Store.java` | Preferências e histórico |
| `MainActivity.java` | A tela única, montada em código |

## Licença

MIT
