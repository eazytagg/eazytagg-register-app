# PDynamics Registration

Self-service kiosk for registering at an event. Separate from the EazyTagg Agent (check-in) app.

- No login and no PIN. Staff enter only the server computer’s IPv4 address and event code once; tap the logo three times in a row to open the staff settings.
- The form, logo, background, accent colour, consent text and end date come from the EazyTagg system and update on refresh.
- First screen asks "Did you preregister?". Yes: scan the QR code or enter the PIN. No: fill in the form.
- Registering goes through `api/submit.entry.php`, which also checks the person in and prints the tag. Works offline and sends later.
- After a result the screen counts down, then clears and returns to the start.

## IP-only setup

Enter an address such as `192.168.0.39` and the event code in separate fields. Do not enter a URL, scheme, port or folder. The app checks the PDynamics deployment at `/eazytagg_test/`, `/` and `/PDynamics/` on that same computer, and remembers the working path. Discovery reads event settings only; it never registers or checks in a guest. Existing cached server connections stay intact until a new connection succeeds.


## Latest PDynamics test download

The `test-latest` GitHub prerelease contains the current test APK and `build-info.json` with the source commit and SHA-256 checksum. The office Git Pull page retrieves the selected app's source and matching APK into `APKS/builds`. Wait for the GitHub build to finish before downloading.

GitHub Actions runs the app tests and validates compilation. The update-compatible APK is signed and published from the office computer, keeping its signing key local. Git Pull retrieves that published release. CI does not overwrite the release with a differently signed validation build. Do not install the CI validation artifact over an existing preview. An explicitly approved, separately configured `PDYNAMICS_TEST_KEYSTORE_B64` secret can enable automated publishing later. Never commit a keystore or its base64 value. These are internal test builds, not Play Store releases.

Current preview: **0.2.2**. Includes responsive portrait/landscape registration, pinned Submit/Cancel actions, centred check-in buttons, full Powered by wordmark, event artwork Banner/Logo modes, IP-based local-server discovery, and durable offline registration storage retaining consent choices. The local server supplies artwork settings through `reg_ui.artwork_mode`.
