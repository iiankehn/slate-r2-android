const nav = document.querySelector("#site-nav");
const menuButton = document.querySelector(".menu-button");


menuButton?.addEventListener("click", () => {
  const open = nav.classList.toggle("open");
  menuButton.setAttribute("aria-expanded", String(open));
});
nav?.querySelectorAll("a").forEach((link) => link.addEventListener("click", () => {
  nav.classList.remove("open");
  menuButton?.setAttribute("aria-expanded", "false");
}));

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

