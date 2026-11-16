# Google / Firebase / backend authentication

Android obtains one Google ID token with Credential Manager, signs into Firebase with it,
then sends that same Google token to `POST /api/v1/auth/google`. The backend retains
GoogleIdTokenVerifier verification and returns its own JWT. Android stores that JWT in
its existing DataStore and Retrofit attaches it to backend requests. No Firebase token
verification or automatic refresh is used. Sign in again after backend token expiry.

Set `GOOGLE_CLIENT_ID` on the backend to the web OAuth client ID from Android's
`app/google-services.json` (client_type 3 / generated default_web_client_id):

`780562078421-6nu5vrco61cou64ah897bq9eemqnb5gk.apps.googleusercontent.com`

This OAuth client ID is public configuration, not a secret. Do not commit JWT signing
secrets or ID tokens. Configure the deployed backend separately; a branch push alone
does not prove deployment or environment alignment.

Guest mode has no backend JWT. Logout clears DataStore's JWT and Firebase's local
session. It does not revoke already issued backend JWTs, which retain the configured
expiry. Existing DataStore storage is app-private but not additionally encrypted.

The old test-token endpoint and test-bypass shortcut are removed. User and Spark
routes require authentication; health and the existing Stripe webhook remain public.
