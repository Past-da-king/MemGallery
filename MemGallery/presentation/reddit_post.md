# Reddit Post for MemGallery

## Title:

**Looking for alpha testers for MemGallery - like Nothing Essential Space but for any Android phone**

---

## Post:

Hey everyone!

I'm looking for testers for my app **MemGallery**, which is currently in alpha.

**What is it?**

MemGallery is inspired by Nothing Essential Space and OPPO Mind Space. It works pretty much the same way, but obviously not as polished since it's not a first-party app. Also, this is my first Android app, so any help would be much appreciated.

**What it does:**

It's basically your external brain. You save stuff to it and it processes everything with AI.

- You can upload images
- Turn on auto screenshot indexing (off by default for privacy, you gotta enable it yourself)
- When you take a screenshot it gets sent to the app and analyzed
- Saves bookmarks, audio notes, text notes, whatever
- The AI looks at everything and suggests tasks or events based on what you saved
- Like if you screenshot a concert poster it'll pull out the date and suggest adding it to your calendar

**The task stuff:**

It can suggest actions from your memories. You can either use the built-in task manager (which is pretty basic ngl, still working on it) or just send it to whatever you already use - Google Calendar, your own task app, whatever.

You can also just turn off the whole task manager section if you don't want it. Then it's just the memory gallery.

**Customization:**

- Uses Material You so it's dynamic based on your wallpaper
- But you can also manually pick colors if you want
- Has AMOLED mode for pure blacks
- Pretty much everything can be toggled on/off

**Why I made this:**

Not everyone has a Nothing phone or OPPO device but I think everyone should have access to this kind of smart memory system. So yeah, made it universal.

It's in alpha right now so there's definitely rough edges. The task manager is very basic. But the core memory stuff and AI analysis works pretty well.

If you wanna test it, you can get it here: https://github.com/Past-da-king/MemGallery/releases

Would appreciate any feedback or bug reports!

---

**Tech stuff if you care:**
- Kotlin + Jetpack Compose
- Gemini API for the AI
- Material You theming
- Room for local storage

**Privacy:**
Screenshot auto-indexing is OFF by default. You control what gets analyzed.
