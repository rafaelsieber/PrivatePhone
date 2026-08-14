# PrivatePhone

App Android minimalista que **só deixa passar ligações de quem está na sua agenda**. Todo o resto é recusado antes de o telefone tocar — sem toque, sem vibração, sem notificação.

29 KB, zero dependências externas, zero acesso à internet. A única permissão que ele pede é `READ_CONTACTS`.

## Como funciona

Ele **não substitui o discador**. Usa a API dedicada do Android para isso, o [`CallScreeningService`](https://developer.android.com/reference/android/telecom/CallScreeningService): o sistema consulta o app antes de a chamada tocar, e o app responde se ela passa ou não.

Você continua usando seu app de telefone normal para ligar. Este só filtra o que chega.

Para um número desconhecido a resposta é `setRejectCall(true)` — a chamada é derrubada na operadora, não apenas silenciada no aparelho.

### Decisões de projeto

- **Número oculto/privado é bloqueado.** Não está na agenda, não passa.
- **Sem permissão de contatos, libera tudo.** É proposital: se a permissão for revogada, o app não tem como saber quem é conhecido. Melhor deixar um desconhecido passar do que bloquear a família inteira por causa de uma permissão que sumiu.
- **Casamento de números tolerante ao 9º dígito.** Além do `PhoneLookup` do Android, compara os últimos 8 dígitos. Um contato salvo como `9999-8888` casa com `+55 11 99999-8888`.
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

Quatro arquivos, em `app/src/main/java/com/rafael/privatephone/`:

| Arquivo | Papel |
|---|---|
| `ContactScreeningService.java` | Decide passa/bloqueia a cada chamada |
| `Contacts.java` | Procura o número na agenda |
| `Store.java` | Preferências e histórico |
| `MainActivity.java` | A tela única, montada em código |

## Licença

MIT
