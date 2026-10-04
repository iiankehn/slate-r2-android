const nav = document.querySelector("#site-nav");
const menuButton = document.querySelector(".menu-button");
const themeButton = document.querySelector(".theme-button");

menuButton?.addEventListener("click", () => {
  const open = nav.classList.toggle("open");
  menuButton.setAttribute("aria-expanded", String(open));
});
nav?.querySelectorAll("a").forEach((link) => link.addEventListener("click", () => {
  nav.classList.remove("open");
  menuButton?.setAttribute("aria-expanded", "false");
}));

const root = document.documentElement;
const storedTheme = localStorage.getItem("slate-theme");
if (storedTheme === "light" || storedTheme === "dark") root.dataset.theme = storedTheme;
const syncThemeControl = () => {
  const dark = root.dataset.theme ? root.dataset.theme === "dark" : matchMedia("(prefers-color-scheme: dark)").matches;
  themeButton.textContent = dark ? "Light" : "Dark";
  themeButton.setAttribute("aria-label", `Switch to ${dark ? "light" : "dark"} theme`);
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", dark ? "#0f141d" : "#f4f6fb");
};
syncThemeControl();
themeButton?.addEventListener("click", () => {
  const systemDark = matchMedia("(prefers-color-scheme: dark)").matches;
  const next = root.dataset.theme === "dark" ? "light" : root.dataset.theme === "light" ? "dark" : systemDark ? "light" : "dark";
  root.dataset.theme = next;
  localStorage.setItem("slate-theme", next);
  syncThemeControl();
});

const setSlate = (selector, value) => {
  document.querySelectorAll(`[data-product="slate"][data-${selector}]`).forEach((node) => {
    if (selector.endsWith("-link")) node.href = value;
    else node.textContent = value;
  });
};

(async () => {
  try {
    const response = await fetch("https://api.github.com/repos/iiankehn/slate-android/releases/latest", {
      headers: { Accept: "application/vnd.github+json" }
    });
    if (!response.ok) throw new Error("No public release");
    const release = await response.json();
    const apk = release.assets?.find((asset) => asset.name.toLowerCase().endsWith(".apk"));
    if (!apk) throw new Error("No APK asset");
    setSlate("release-link", apk.browser_download_url);
    setSlate("release-version", release.name || release.tag_name);
    setSlate("release-status", "Signed release available");
    setSlate("download-copy", "Latest signed monthly APK with verified update metadata.");
    const checksum = release.assets?.find((asset) => asset.name.toLowerCase().endsWith(".sha256"));
    if (checksum) setSlate("checksum-link", checksum.browser_download_url);
    document.querySelectorAll('[data-product="slate"]').forEach((node) => {
      node.closest(".product-card")?.querySelector(".status-dot")?.classList.replace("pending", "live");
    });
  } catch {
    // Static links already point to the official release page.
  }
})();
