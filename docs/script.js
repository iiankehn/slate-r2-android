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

const products = {
  r1: {
    repo: "iiankehn/slate-android",
    fallback: "https://github.com/iiankehn/slate-android/releases",
    readyCopy: "Latest signed monthly APK with verified update metadata."
  },
  r2: {
    repo: "iiankehn/slate-r2-android",
    fallback: "https://github.com/iiankehn/slate-r2-android/releases",
    readyCopy: "Latest signed monthly R2 APK."
  }
};

const setProduct = (key, selector, value) => {
  document.querySelectorAll(`[data-product="${key}"][data-${selector}]`).forEach((node) => {
    if (selector.endsWith("-link")) node.href = value;
    else node.textContent = value;
  });
};

Object.entries(products).forEach(async ([key, product]) => {
  try {
    const response = await fetch(`https://api.github.com/repos/${product.repo}/releases/latest`, {
      headers: { Accept: "application/vnd.github+json" }
    });
    if (!response.ok) throw new Error("No public release");
    const release = await response.json();
    const apk = release.assets?.find((asset) => asset.name.toLowerCase().endsWith(".apk"));
    if (!apk) throw new Error("No APK asset");
    setProduct(key, "release-link", apk.browser_download_url);
    setProduct(key, "release-version", release.name || release.tag_name);
    setProduct(key, "release-status", "Signed release available");
    setProduct(key, "download-copy", product.readyCopy);
    const checksum = release.assets?.find((asset) => asset.name.toLowerCase().endsWith(".sha256"));
    if (checksum) setProduct(key, "checksum-link", checksum.browser_download_url);
    document.querySelectorAll(`[data-product="${key}"]`).forEach((node) => {
      const card = node.closest(".product-card");
      card?.querySelector(".status-dot")?.classList.replace("pending", "live");
    });
  } catch {
    document.querySelectorAll(`[data-product="${key}"][data-release-link]`).forEach((node) => {
      if (!node.href) node.href = product.fallback;
    });
  }
});
