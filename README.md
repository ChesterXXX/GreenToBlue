# GreenToBlue

With the new chat import feature of Telegram (praise Durov!), I tried to export all my previous chat from the evil green app. Turns out, the evil green app has very limited **chat import** features, as outlines [here](https://faq.whatsapp.com/android/chats/how-to-save-your-chat-history/?lang=en).

Some light Google-fu led me to this [XDA Post](https://forum.xda-developers.com/t/tool-whatsapp-xtract-backup-messages-extractor-database-analyzer-chat-backup.1583021/). Inspired by this, I wrote this nifty little app to export full chat, including all media, from Whatsapp to Telegram.

And it can do the same with Facebook chat as well!

<h3>DISCLAIMER</h3> This is basically my first ever android project, at least in Kotlin. Made it in order to learn the language. So this doesn't abide by any sort of best practices. Also it is really finicky. And has some memory leaks and other nice "features". But hey! If it works...

<h3>File Access Permission</h3>
One of the "bad" practices in this app is the file access permission. In my (very brief) experience, file system access in Android is an absolute hot mess. I resorted to asking for full storage permission, which is in fact deprecated. And might not even work in Android R. If the app doesn't ask for the permission or in case it crashes every time, please manually allow full file access permision in the device settings.

<h5>Before you upload</h5>
Also keep in mind that Telegram will import <b>all</b> the chats at the very end of your target chat. It will <b>not</b> mix up the old chats with the imported chats based on timestamp. Hence I would strongly suggest that you create some empty groups and import into them.

<h2>Brief Tutorial</h2>

<h3>How to export whatsapp chat?</h3>
You need access to two .db files, namely, <i>msgstore.db</i> and <i>wa.db</i>. The first one contains all your chat data, unencrypted! The second one contains the actual names of your whtasapp contacts. Both are SQLite databases, you can easily browse them using <a href=https://sqlitebrowser.org/>DB Browsser</a>
<br><br>
<p>
The problem is, you need to have a rooted android phone to access these files, since they are located in <i>/data/data/com.whatsapp/databases/</i>
</p>
<br>
<p>
So root your phone! Or not... Here's a workaround (a very ineffecive workaround)
</p>

<ul>
<li> Backup your whatsapp chat to Google Drive (no need to backup media)
<li> Setup an android virtual devide on a PC
<li> Install whatsapp on this virtual device
<li> Root this virtual device
<li> Extract the two .db files using adb
<li> Profit?
</ul>

Also, I hypothesize that the above process should work for iOS users as well, since all you need to do on your device is the backup to GDrive, which is present in iPhones (I think).

Now that you have the <i>.db</i> files, locate them in the app. Also locate the media folder. Usually its in <i>/Whatsapp/media/</i> of your device.

<h3>How to export facebook chat?</h3>
In you FB profile settings, there should be an option to download all your data. Select JSON format and download all your messages. After some time, you'll be notified that your download is ready. Download the zip file to your device. Extract it somewhere. Locate the messages folder in the app.

When scanning the FB chat data, the app will ask for your name. This is because it is impossible to detect from the JSONs which chat are from you and which are from other users.

<h3>Merging chats</h3>
If you long press on a chat, you can then select multiple chats and merge them together! The merged chats will be sorted by timestamps. This is useful if you have one chat in whatsapp and another in FB, with the same person.

<h3>Chunks?!</h3>
Once you select a chat to export, there is an option to make chunk! Why? Well, if you are trying to export some 1000+ media files all at once, most likely your device will just give up! On my device, I could handle ~800 files at once. Experiment on your own device. Once the chunks are created, you have to manually upload each of them. Also keep in mind, you might need to wait for a few minutes between each upload. Telgram throttles too many chat export requests.

<br>

Also, each chunk of chat will begin and end with a distinct message : BEGINNING OF CHUNK # and ENDING OF CHUNK #, from the user <b>Green to Blue</b>. This serves a few purposes.
<ul>
<li>  You can easily search for CHUNK to get to a desired point, especially to the very beginning, of the imported chat. It is highly unlikely that you have many occurences of CHUNK in your existing chat! Note that you can also use the calender in the telegram search as well!
<li> Usually if you try to export a chat with an user from whatsapp, telegram will detect it as a user chat and will not allow import into groups. This is avoided with the presence of these extra two chats from a different user.
<li> If you have an ungodly amount of chunks, the last chat works as a reminder!
</ul>
Once all the chunks are uploaded, please manually search for all the CHUNK and delete them from your chat (if you wish).
