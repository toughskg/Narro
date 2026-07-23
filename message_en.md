# Narro English Message Catalog

## Authoring rules

- Informational messages use `msg_`, warnings use `alt_`, and errors use `err_`.
- IDs must remain aligned with `message_ko.md` and must never be reused for a different meaning.
- `%1$s` is a string argument and `%1$d` is an integer argument.
- File names and imported document content must not be translated or modified.
- Keep punctuation in the Android string resources.

## Informational messages

```properties
msg_001=Enter a 4-digit numeric PIN.
msg_002=Enter the PIN again to confirm it.
msg_003=PIN registered.
msg_004=PIN changed.
msg_005=App Lock turned on.
msg_006=App Lock turned off and the existing PIN was reset.
msg_007=Register a PIN before turning on biometric unlock.
msg_008=Authenticate to continue.
msg_009=Biometric unlock turned on.
msg_010=Biometric unlock turned off.
msg_011=You can unlock the app with your PIN instead.
msg_012=Checking the file.
msg_013=Detecting the file encoding.
msg_014=Converting the file to UTF-8.
msg_015=Analyzing sentences.
msg_016=Preparing the reading position.
msg_017=Importing %1$s.
msg_018=Import is %1$d%% complete.
msg_019=The file size is unavailable. Showing the amount processed instead.
msg_020=Imported %1$s.
msg_021=File import canceled.
msg_022=Cleaning up temporary files.
msg_023=Files with the same name can be saved as separate documents.
msg_024=Preparing playback.
msg_025=Playback started.
msg_026=Playback paused.
msg_027=Resuming from where you paused.
msg_028=Moved to your previous reading position.
msg_029=This document will start from the beginning.
msg_030=You have reached the end of the document. Returning to Documents.
msg_031=Narro paused because another app started playing audio.
msg_032=Audio is available again. Resuming playback.
msg_033=Playback stopped because your audio device was disconnected.
msg_034=Moved to the current reading position.
msg_035=Auto-scroll paused.
msg_036=Auto-scroll resumed.
msg_037=Bookmark added at the current position.
msg_038=Bookmark deleted.
msg_039=Moved to the bookmark.
msg_040=Reading position saved.
msg_041=Voice changed.
msg_042=Reading speed changed to %1$s.
msg_043=System language settings applied.
msg_044=Display language changed.
msg_045=Switched to the default voice.
msg_046=Document deleted.
msg_047=Settings saved.
msg_048=Documents and settings restored from backup.
msg_049=App Lock is turned off on the restored device.
msg_050=Reading %1$s.
msg_051=Reading will continue in the background.
msg_052=Playback ended and your position was saved.
msg_053=Preparing the next section.
msg_054=Document content checked again.
msg_055=Continuing with the duplicate document import.
```

## Warning messages

```properties
alt_001=We couldn’t detect the file’s character encoding.
alt_002=This file might not contain UTF-8, EUC-KR, or UTF-16 text.
alt_003=This file has no readable content.
alt_004=This does not appear to be a text file.
alt_005=This file is larger than 20 MiB and can’t be imported.
alt_006=If stored documents exceed 20 MiB, Google Drive device backup might not include them.
alt_007=Your documents are approaching the total storage limit.
alt_008=A file with the same content already exists. Import it anyway?
alt_009=A document with the same name already exists. Import it as a separate document?
alt_010=Cancel the import? Content being processed won’t be saved.
alt_011=Delete %1$s?
alt_012=Deleting this document also removes its reading position and bookmarks.
alt_013=Deleted documents can’t be recovered.
alt_014=Turning off App Lock resets the existing PIN and biometric setting. You must register a new PIN when turning it on again.
alt_015=If you forget your PIN, you must clear the app data to use Narro again.
alt_016=After 5 incorrect PIN attempts, you can’t try again for 30 seconds.
alt_017=Incorrect PIN. Try again.
alt_018=You have %1$d PIN attempts remaining.
alt_019=Too many incorrect PIN attempts. Try again in %1$d seconds.
alt_020=Biometric authentication was canceled. Unlock with your PIN.
alt_021=Biometric authentication failed too many times. Unlock with your PIN.
alt_022=No biometric credentials are enrolled. Add one in system settings first.
alt_023=Biometric authentication isn’t available on this device.
alt_024=A voice for the selected language isn’t installed.
alt_025=The saved voice is unavailable. The default voice will be used.
alt_026=The document language and selected voice language are different. Play it anyway?
alt_027=Playback can’t start because another app is using audio.
alt_028=No headphones or Bluetooth device is connected. Play through the device speaker?
alt_029=The document or parsing rules changed. Narro will locate your saved position again.
alt_030=This bookmark might not match the current document.
alt_031=Google Drive device backup might stop if the backup data exceeds 25 MB.
alt_032=App Lock was turned off after restoring the backup. Set it up again.
alt_033=Stop the current playback and open another document?
alt_034=Closing the app saves your position through the last completed sentence.
alt_035=Some items use multiple lines because the system font size is large.
alt_036=This system language isn’t supported. The app will be displayed in English.
alt_037=Not enough storage is available. Free up space and try again.
alt_038=Playback can’t resume in the middle of this sentence. It will restart from the beginning of the sentence.
alt_039=Closing the app during import will cancel the operation.
alt_040=Delete this bookmark?
```

## Error messages

```properties
err_001=A system error occurred.
err_002=Couldn’t save the PIN. Try again.
err_003=Couldn’t load the PIN information.
err_004=An error occurred while checking the PIN.
err_005=The PIN information is damaged. You must clear the app data.
err_006=Couldn’t start biometric authentication.
err_007=An error occurred during biometric authentication.
err_008=Couldn’t open the file. Check the file access permission.
err_009=Couldn’t read the file information.
err_010=Unsupported file format. Select a TXT file.
err_011=Couldn’t automatically detect the file encoding.
err_012=Couldn’t convert the file to UTF-8.
err_013=The file contains invalid character data.
err_014=Couldn’t copy the file.
err_015=Couldn’t save the file.
err_016=Couldn’t import the file because there isn’t enough storage.
err_017=Couldn’t analyze the file.
err_018=Couldn’t create the sentence sections.
err_019=Couldn’t complete the file import.
err_020=Couldn’t clean up the temporary files.
err_021=Couldn’t load the document list.
err_022=Couldn’t open the document.
err_023=The original document is missing. Import it again.
err_024=The original document is damaged and can’t be read.
err_025=Couldn’t load the current section.
err_026=Couldn’t prepare the next section. Try again.
err_027=Couldn’t load the saved reading position.
err_028=Couldn’t save the reading position.
err_029=Couldn’t start the text-to-speech engine.
err_030=The text-to-speech engine isn’t responding.
err_031=The text-to-speech engine doesn’t support the selected language.
err_032=The selected voice is unavailable.
err_033=Couldn’t load the voice data.
err_034=Couldn’t convert the sentence to speech.
err_035=Couldn’t start playback.
err_036=An error occurred during playback.
err_037=Couldn’t get audio focus.
err_038=Couldn’t start background playback.
err_039=Couldn’t show the playback notification. Check the notification permission.
err_040=Couldn’t restore the playback state.
err_041=Couldn’t add the bookmark.
err_042=Couldn’t load the bookmarks.
err_043=Couldn’t find the bookmark position.
err_044=Couldn’t delete the bookmark.
err_045=Couldn’t delete the document. Try again later.
err_046=The document file was deleted, but its related data couldn’t be removed.
err_047=Couldn’t load the settings.
err_048=Couldn’t save the settings.
err_049=Couldn’t open the system app language settings.
err_050=Couldn’t complete the Google Drive device backup.
err_051=Couldn’t restore the backup data.
err_052=The restored data is damaged.
err_053=Couldn’t open the database.
err_054=Couldn’t update the database.
err_055=An error occurred while saving data.
err_056=The required security key is unavailable.
err_057=The security key is no longer valid. Set up App Lock again.
err_058=The operation took too long and was stopped. Try again.
err_059=The requested operation was canceled.
err_060=An unknown error occurred. Restart the app.
```

## Implementation mapping

| Prefix | Default presentation | Default user action |
|---|---|---|
| `msg_` | Inline status, snackbar, progress area, notification | Acknowledge or automatic dismissal |
| `alt_` | Inline warning or confirmation dialog | Continue, cancel, or open settings |
| `err_` | Error banner, error dialog, or full-screen error | Retry, select another file, open settings, or close the app |

- Use `err_001` and `err_060` only as final fallbacks when no specific error applies.
- Log the displayed message ID with the internal error code, but never show stack traces or file paths to the user.
- Replace transient progress messages in the same area instead of stacking snackbars.
- Require confirmation for deletion and duplicate imports. Turning off App Lock applies immediately and must clearly state that the PIN is reset.
- For consistency failures such as `err_046`, offer a retry and write a diagnostic log.
