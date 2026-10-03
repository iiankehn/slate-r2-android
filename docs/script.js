const menuButton = document.querySelector('.menu-button');
const navigation = document.querySelector('#site-nav');
const themeButton = document.querySelector('.theme-button');
const root = document.documentElement;

menuButton?.addEventListener('click', () => {
  const open = navigation.classList.toggle('open');
  menuButton.setAttribute('aria-expanded', String(open));
});

navigation?.addEventListener('click', () => {
  navigation.classList.remove('open');
  menuButton?.setAttribute('aria-expanded', 'false');
});

const savedTheme = localStorage.getItem('slate-theme');
if (savedTheme === 'light' || savedTheme === 'dark') root.dataset.theme = savedTheme;

themeButton?.addEventListener('click', () => {
  const systemDark = matchMedia('(prefers-color-scheme: dark)').matches;
  const currentDark = root.dataset.theme ? root.dataset.theme === 'dark' : systemDark;
  root.dataset.theme = currentDark ? 'light' : 'dark';
  localStorage.setItem('slate-theme', root.dataset.theme);
});

fetch('https://api.github.com/repos/iiankehn/slate-r2-android/releases/latest', {
  headers: { Accept: 'application/vnd.github+json' }
}).then(response => {
  if (!response.ok) throw new Error('No published release');
  return response.json();
}).then(release => {
  const apk = release.assets?.find(asset => asset.name.toLowerCase().endsWith('.apk'));
  if (!apk) return;
  document.querySelectorAll('[data-release-link]').forEach(link => link.href = apk.browser_download_url);
  document.querySelectorAll('[data-release-version]').forEach(label => label.textContent = release.name || release.tag_name);
  document.querySelectorAll('[data-release-status]').forEach(label => label.textContent = `${release.name || release.tag_name} · signed sideload`);
  document.querySelectorAll('[data-download-copy]').forEach(label => label.textContent = 'Download the signed universal APK directly from the official Slate R2 GitHub release.');
}).catch(() => {
  // The static release-candidate copy remains visible until the first signed release is published.
});
