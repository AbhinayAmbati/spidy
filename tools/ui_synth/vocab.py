"""Fictional vocabulary and app definitions. Everything here is invented; no real people, places or messages."""
from __future__ import annotations

FIRST = ["Mira", "Tobin", "Lenka", "Orrin", "Pella", "Davo", "Suri", "Kelvo", "Nia", "Brannoc",
         "Ilsa", "Torvi", "Quen", "Haldor", "Yara", "Fennic", "Zeda", "Osric", "Lumi", "Varo"]
LAST = ["Quill", "Vale", "Marrow", "Thorne", "Lumen", "Ashby", "Corvin", "Pellam", "Dray", "Sorrel",
        "Wick", "Ember", "Hollis", "Brindle"]
CAPTIONS = ["Sunny day in the garden", "New recipe test", "Rainy afternoon vibes", "Finished my puzzle",
            "Look at these clouds", "Trying a new hobby", "Weekend project done", "Cozy reading corner",
            "Fresh bread today", "Morning walk views", "Paper boat race", "Tiny robot build"]
MESSAGES = ["running a bit late", "see you at the park", "the box arrived safely", "lets meet after lunch",
            "did you eat yet", "sounds good to me", "call me when free", "bringing snacks for everyone",
            "thanks for the help", "that movie was fun", "on my way now", "happy to join"]
QUERIES = ["pasta recipes", "funny cats", "rain sounds", "weather tomorrow", "guitar lessons", "paper boats",
           "chess openings", "indoor plants", "lofi music", "bread baking", "star maps", "puzzle games"]
RESULTS = ["Beginner guide", "Top picks this week", "Easy tutorial", "Popular near you", "Quick tips",
           "Complete collection", "Starter pack", "Daily highlights", "Community favorites"]
FOLDERS = ["Documents", "Downloads", "Pictures", "Music", "Videos", "Backups", "Notes", "Projects", "Archive"]
SETTINGS = ["Wi-Fi", "Bluetooth", "Display", "Sound", "Battery", "Storage", "Notifications", "Location",
            "Mobile data", "Hotspot", "Dark mode", "Airplane mode", "Do not disturb", "Accessibility",
            "Security", "Privacy", "Apps", "Wallpaper", "Language", "Date and time"]
TOGGLES = ["Wi-Fi", "Bluetooth", "Dark mode", "Airplane mode", "Do not disturb", "Hotspot", "Location"]
LAUNCHER_FILLER = ["Notes", "Clock", "Camera", "Calculator", "Gallery", "Weather", "Music", "Phone"]
SAFE_MENU = ["Settings", "Share", "Help", "Mute", "Report", "Archive"]
RISKY_MENU = ["Delete chat", "Log out", "Clear history"]

# Apps. tabs: (position, labels). templates: screen kinds the app can show.
APPS = {
    "instagram": dict(name="Instagram", tabs=("bottom", ["Home", "Search", "Reels", "Messages", "Profile"]),
                      templates=["feed", "search", "profile", "chat_list", "chat_view", "menu", "dialog"]),
    "whatsapp": dict(name="WhatsApp", tabs=("top", ["Chats", "Updates", "Communities", "Calls"]),
                     templates=["chat_list", "chat_view", "call_list", "settings_list", "menu", "dialog"]),
    "telegram": dict(name="Telegram", tabs=("bottom", ["Contacts", "Calls", "Chats", "Settings"]),
                     templates=["chat_list", "chat_view", "search", "settings_list", "menu", "call_list", "dialog"]),
    "twitter": dict(name="Twitter", tabs=("bottom", ["Home", "Search", "Spaces", "Notifications", "Messages"]),
                    templates=["feed", "search", "profile", "menu", "dialog"]),
    "google": dict(name="Google", tabs=("bottom", ["Discover", "Search", "Updates"]),
                   templates=["search", "feed", "menu", "dialog"]),
    "settings": dict(name="Settings", tabs=None, templates=["settings_list", "search", "menu"]),
    "youtube": dict(name="YouTube", tabs=("bottom", ["Home", "Shorts", "Subscriptions", "You"]),
                    templates=["feed", "search", "profile", "menu", "dialog"]),
    "files": dict(name="Files", tabs=("bottom", ["Browse", "Recent", "Cleaner"]),
                  templates=["file_list", "search", "menu", "dialog"]),
    "gmail": dict(name="Gmail", tabs=("bottom", ["Mail", "Meet"]),
                  templates=["chat_list", "chat_view", "search", "menu", "dialog"]),
    "maps": dict(name="Maps", tabs=("bottom", ["Explore", "Saved", "Updates"]),
                 templates=["search", "menu", "dialog", "profile"]),
}
# Held out entirely from training: these templates and these apps.
TEST_TEMPLATES = {"call_list", "menu", "ad_popup"}
TEST_APPS = {"maps", "files"}
