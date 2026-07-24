# bank-core — Gestion centralisée des exceptions (Spring Boot 3)

Implémentation de référence d'une architecture de gestion d'exceptions
**centralisée** pour une application bancaire, garantissant **zéro fuite** de
détails techniques (stack trace, message SQL, nom de classe) vers les clients —
REST comme JSF — tout en conservant des **logs complets corrélés par un
`ErrorId`**.

- **Stack** : Spring Boot 3.3 · Java 17 · Spring Security 6 · Jakarta Faces 4 (Mojarra) · architecture hexagonale.
- **Document de conception détaillé** : voir [`docs/architecture-gestion-exceptions.md`](docs/architecture-gestion-exceptions.md).

## Principe

Quel que soit le canal, l'utilisateur ne reçoit qu'un **message générique + un
`ErrorId`**. Le serveur conserve la **trace complète** corrélée par ce même
`ErrorId` (MDC + logs). Format de l'ErrorId : `ERR-20260724-3F9K2A7Q`
(horodaté + aléatoire, alphabet Crockford dictable au téléphone).

## Composants clés

| Package | Rôle |
|---|---|
| `domain.exception` | Exceptions métier **pures** (aucune dépendance framework). |
| `…web.error.ErrorCode` | Catalogue public stable (`BANK-xxxx`) : HTTP status + clé i18n + niveau de log. |
| `…web.error.ApiError` | Modèle de réponse JSON immuable — uniquement des champs sûrs. |
| `…web.error.ErrorIdGenerator` | Génération de l'`ErrorId`. |
| `…web.error.ErrorLogger` | Journalisation unique (ERROR pour 5xx, WARN pour 4xx) + MDC. |
| `…web.error.ApiErrorFactory` | Construction de la réponse (i18n, masquage prod). |
| `…web.error.GlobalRestExceptionHandler` | Point d'entrée REST unique (`@RestControllerAdvice`). |
| `…web.security.RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` | 401 / 403 JSON. |
| `…web.jsf.JsfExceptionHandler` / `JsfExceptionHandlerFactory` | Interception JSF → page d'erreur avec `ErrorId`. |

## Build & tests

```bash
mvn verify
```

- Tests unitaires (`*Test`) : `ErrorIdGenerator`, `ApiErrorFactory`.
- Tests d'intégration (`*IT`) : `GlobalRestExceptionHandlerIT` (mapping, anti-fuite
  paramétré), `SecurityErrorIT` (401/403).

## Exemple de réponse (500 — aucun détail interne)

```json
{
  "errorId": "ERR-20260724-QW3E9R7T",
  "code": "BANK-9000",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Une erreur interne est survenue. Merci de contacter le support avec la référence indiquée.",
  "path": "/api/v1/transfers",
  "timestamp": "2026-07-24T10:17:05.500+02:00"
}
```

## Configuration prod vs dev

`app.errors.expose-technical-details` (false en prod) pilote l'ajout d'un champ
`debug` en environnement de développement uniquement. En prod, `server.error.*`
est verrouillé (`include-stacktrace: never`, whitelabel désactivé).

## Intégration JSF/PrimeFaces réelle

Le handler/factory JSF compilent contre l'API Jakarta Faces (Mojarra). Pour un
runtime JSF complet intégré à Spring Boot, ajouter le starter
[joinfaces](https://github.com/joinfaces/joinfaces) + PrimeFaces, puis déployer
`error.xhtml` / `login.xhtml`.
