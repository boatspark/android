# Future true push notifications

BoatSpark currently refreshes cached data with WorkManager and does not create
notifications from polling. True alert push requires coordinated server and
Android work:

1. Select and configure a push transport, normally Firebase Cloud Messaging.
2. Add authenticated API endpoints to register, rotate, and delete a user's
   device token. Store registrations per user and device on the server.
3. Publish push messages from server-side alert processing only after the alert
   transaction commits. Define retry, invalid-token cleanup, and observability.
4. Keep payloads non-sensitive. Send an alert ID, boat ID, and event category;
   fetch current details through the authenticated API when the user opens it.
5. Add Android notification channels, Android 13+ notification permission,
   token rotation handling, logout deletion, and deep links to the matching
   boat alert.
6. Test duplicate delivery, delayed/out-of-order delivery, token expiration,
   user removal, inactive accounts, and a signed-out device.

Do not add a client-side timer that imitates push. WorkManager's hourly
periodic refresh is inexact and is intended only to refresh the offline cache.

