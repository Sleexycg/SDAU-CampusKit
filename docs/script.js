(() => {
  const header = document.querySelector('[data-header]');
  const progress = document.querySelector('[data-progress]');
  const menuButton = document.querySelector('[data-menu-button]');
  const mobileNav = document.querySelector('[data-mobile-nav]');
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const apkLinks = [...document.querySelectorAll('[data-apk-download]')];
  if (apkLinks.length) {
    fetch('https://api.github.com/repos/Sleexycg/WeSDAU/contents/docs/downloads', {
      headers: { Accept: 'application/vnd.github+json' },
    })
      .then((response) => (response.ok ? response.json() : []))
      .then((files) => {
        const apk = files.find((file) => file.type === 'file' && file.name.toLowerCase().endsWith('.apk'));
        if (!apk) return;
        const downloadUrl = `./downloads/${encodeURIComponent(apk.name)}`;
        apkLinks.forEach((link) => {
          link.href = downloadUrl;
        });
      })
      .catch(() => {
        // Keep the directory fallback when the GitHub API is temporarily unavailable.
      });
  }

  const typewriter = document.querySelector('[data-typewriter]');
  const typewriterLines = [
    '课表再多也清楚',
    '考试再忙也不慌',
    '成绩再杂也能查',
    '教室再远也好找',
    '安排再满也从容',
  ];
  const typewriterColors = ['#1769e8', '#8b5cf6', '#0f9f8f', '#e17b55', '#d14d72'];
  let typewriterTimer = 0;

  if (typewriter) {
    if (reducedMotion) {
      typewriter.textContent = typewriterLines[0];
      typewriter.style.setProperty('--type-color', typewriterColors[0]);
    } else {
      let lineIndex = 0;
      let characterIndex = Array.from(typewriterLines[0]).length;
      let deleting = true;
      typewriter.style.setProperty('--type-color', typewriterColors[0]);

      const advanceTypewriter = () => {
        const characters = Array.from(typewriterLines[lineIndex]);
        typewriter.style.setProperty('--type-color', typewriterColors[lineIndex]);

        if (deleting) {
          characterIndex -= 1;
          typewriter.textContent = characters.slice(0, characterIndex).join('');

          if (characterIndex === 0) {
            deleting = false;
            lineIndex = (lineIndex + 1) % typewriterLines.length;
            typewriterTimer = window.setTimeout(advanceTypewriter, 48);
            return;
          }

          typewriterTimer = window.setTimeout(advanceTypewriter, 38);
          return;
        }

        characterIndex += 1;
        typewriter.textContent = Array.from(typewriterLines[lineIndex]).slice(0, characterIndex).join('');

        if (characterIndex === Array.from(typewriterLines[lineIndex]).length) {
          deleting = true;
          typewriterTimer = window.setTimeout(advanceTypewriter, 680);
          return;
        }

        typewriterTimer = window.setTimeout(advanceTypewriter, 78);
      };

      typewriterTimer = window.setTimeout(advanceTypewriter, 1100);
    }
  }

  const parallaxStage = document.querySelector('[data-parallax-stage]');
  let parallaxFrame = 0;

  if (parallaxStage && !reducedMotion) {
    const updateParallax = (event) => {
      if (window.innerWidth < 860) return;
      const bounds = parallaxStage.getBoundingClientRect();
      const relativeX = (event.clientX - bounds.left) / bounds.width - 0.5;
      const relativeY = (event.clientY - bounds.top) / bounds.height - 0.5;

      if (parallaxFrame) window.cancelAnimationFrame(parallaxFrame);
      parallaxFrame = window.requestAnimationFrame(() => {
        parallaxStage.style.setProperty('--deck-x', `${(-relativeY * 3).toFixed(2)}deg`);
        parallaxStage.style.setProperty('--deck-y', `${(relativeX * 4).toFixed(2)}deg`);
        parallaxFrame = 0;
      });
    };

    const resetParallax = () => {
      parallaxStage.style.setProperty('--deck-x', '0deg');
      parallaxStage.style.setProperty('--deck-y', '0deg');
    };

    parallaxStage.addEventListener('pointermove', updateParallax, { passive: true });
    parallaxStage.addEventListener('pointerleave', resetParallax);
  }

  const updatePageChrome = () => {
    const scrollTop = window.scrollY;
    const scrollable = document.documentElement.scrollHeight - window.innerHeight;
    header?.classList.toggle('is-scrolled', scrollTop > 12);
    if (progress) {
      const ratio = scrollable > 0 ? Math.min(1, Math.max(0, scrollTop / scrollable)) : 0;
      progress.style.transform = `scaleX(${ratio})`;
    }
  };

  let frame = 0;
  const scheduleChromeUpdate = () => {
    if (frame) return;
    frame = window.requestAnimationFrame(() => {
      frame = 0;
      updatePageChrome();
    });
  };

  window.addEventListener('scroll', scheduleChromeUpdate, { passive: true });
  window.addEventListener('resize', scheduleChromeUpdate);
  updatePageChrome();

  const closeMenu = () => {
    menuButton?.setAttribute('aria-expanded', 'false');
    menuButton?.setAttribute('aria-label', '打开导航');
    mobileNav?.classList.remove('is-open');
    header?.classList.remove('is-menu-open');
    document.body.classList.remove('is-menu-open');
  };

  menuButton?.addEventListener('click', () => {
    const shouldOpen = menuButton.getAttribute('aria-expanded') !== 'true';
    menuButton.setAttribute('aria-expanded', String(shouldOpen));
    menuButton.setAttribute('aria-label', shouldOpen ? '关闭导航' : '打开导航');
    mobileNav?.classList.toggle('is-open', shouldOpen);
    header?.classList.toggle('is-menu-open', shouldOpen);
    document.body.classList.toggle('is-menu-open', shouldOpen);
  });

  mobileNav?.querySelectorAll('a').forEach((link) => link.addEventListener('click', closeMenu));
  window.addEventListener('resize', () => {
    if (window.innerWidth > 1100) closeMenu();
  });

  const reveals = document.querySelectorAll('.reveal');
  reveals.forEach((element) => {
    if (element.dataset.delay) element.style.setProperty('--reveal-delay', `${element.dataset.delay}ms`);
  });

  if (reducedMotion || !('IntersectionObserver' in window)) {
    reveals.forEach((element) => element.classList.add('is-visible'));
  } else {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-visible');
        observer.unobserve(entry.target);
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.08 });
    reveals.forEach((element) => observer.observe(element));
  }

  const screenImage = document.querySelector('[data-screen-image]');
  const deviceShell = document.querySelector('[data-device-shell]');
  const screenTitle = document.querySelector('[data-screen-title]');
  const screenNumber = document.querySelector('[data-screen-number]');
  const screenDescription = document.querySelector('[data-screen-description]');
  const tabs = [...document.querySelectorAll('[data-shot]')];

  const switchScreen = async (tab) => {
    if (!screenImage || tab.classList.contains('is-active')) return;
    deviceShell?.classList.toggle('is-raw-shot', tab.dataset.raw === 'true');
    tabs.forEach((item) => {
      const active = item === tab;
      item.classList.toggle('is-active', active);
      item.setAttribute('aria-selected', String(active));
    });

    screenImage.classList.add('is-changing');
    await new Promise((resolve) => window.setTimeout(resolve, reducedMotion ? 0 : 150));
    screenImage.src = tab.dataset.src;
    screenImage.alt = tab.dataset.alt;
    if (screenTitle) screenTitle.textContent = tab.dataset.title;
    if (screenNumber) screenNumber.textContent = tab.querySelector('span')?.textContent || '';
    if (screenDescription) screenDescription.textContent = tab.dataset.copy;

    try {
      await screenImage.decode();
    } catch {
      // The image can still render when decode() is unavailable or interrupted.
    }
    screenImage.classList.remove('is-changing');
  };

  tabs.forEach((tab) => tab.addEventListener('click', () => switchScreen(tab)));

  document.querySelectorAll('[data-nav-shot]').forEach((hotspot) => {
    hotspot.addEventListener('click', () => {
      const target = tabs.find((tab) => tab.dataset.shot === hotspot.dataset.navShot);
      if (target) switchScreen(target);
    });
  });

  const themeSwitchers = document.querySelectorAll('[data-theme-switcher]');
  themeSwitchers.forEach((switcher) => {
    const image = switcher.querySelector('[data-theme-image]');
    const buttons = [...switcher.querySelectorAll('[data-theme-src]')];

    buttons.forEach((button) => button.addEventListener('click', async () => {
      if (!image) return;
      const selectedButton = button.classList.contains('is-active')
        ? buttons[(buttons.indexOf(button) + 1) % buttons.length]
        : button;

      buttons.forEach((item) => {
        const active = item === selectedButton;
        item.classList.toggle('is-active', active);
        item.setAttribute('aria-pressed', String(active));
      });

      image.classList.add('is-changing');
      await new Promise((resolve) => window.setTimeout(resolve, reducedMotion ? 0 : 160));
      image.src = selectedButton.dataset.themeSrc;
      image.alt = selectedButton.dataset.themeAlt || image.alt;

      try {
        await image.decode();
      } catch {
        // Keep the selected theme visible even when decode() is interrupted.
      }

      image.classList.remove('is-changing');
    }));
  });

  const dialog = document.querySelector('[data-image-dialog]');
  const dialogImage = document.querySelector('[data-dialog-image]');
  const openCurrent = document.querySelector('[data-open-current]');
  const dialogClose = document.querySelector('[data-dialog-close]');

  const openDialog = () => {
    if (!dialog || !dialogImage || !screenImage) return;
    dialogImage.src = screenImage.src;
    dialogImage.alt = `${screenImage.alt}放大预览`;
    if (typeof dialog.showModal === 'function') dialog.showModal();
  };

  openCurrent?.addEventListener('click', openDialog);
  dialogClose?.addEventListener('click', () => dialog?.close());
  dialog?.addEventListener('click', (event) => {
    const bounds = dialog.getBoundingClientRect();
    const inside = event.clientX >= bounds.left && event.clientX <= bounds.right
      && event.clientY >= bounds.top && event.clientY <= bounds.bottom;
    if (!inside) dialog.close();
  });

  const faqItems = [...document.querySelectorAll('.faq-item')];
  faqItems.forEach((item) => item.addEventListener('toggle', () => {
    if (!item.open) return;
    faqItems.forEach((other) => {
      if (other !== item) other.open = false;
    });
  }));

  const year = document.querySelector('[data-year]');
  if (year) year.textContent = String(new Date().getFullYear());

  window.addEventListener('pagehide', () => {
    window.clearTimeout(typewriterTimer);
    if (parallaxFrame) window.cancelAnimationFrame(parallaxFrame);
  });
})();
