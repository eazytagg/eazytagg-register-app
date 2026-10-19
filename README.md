# EazyTagg Register

Self-service kiosk for registering at an event. Separate from the EazyTagg Agent (check-in) app.

- No login. Staff set the server, event code and a staff PIN once.
- The form, logo, background, accent colour, consent text and end date come from the EazyTagg system and update on refresh.
- First screen asks "Did you preregister?". Yes: scan the QR code or enter the PIN. No: fill in the form.
- Registering goes through `api/submit.entry.php`, which also checks the person in and prints the tag. Works offline and sends later.
- After a result the screen counts down, then clears and returns to the start.
