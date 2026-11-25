(() => {
  // =========================================================================
  // 1. Main 16:9 Walkthrough Player & Interactive Chapter Scrubber
  // =========================================================================
  const videoStage = document.getElementById('video-stage');
  const mainVideo = document.getElementById('main-video');
  const posterPlay = document.getElementById('poster-play-overlay');
  const heroPlayBtn = document.getElementById('hero-play-btn');
  const footerPlayBtn = document.getElementById('footer-play-btn');
  const chapterBtns = Array.from(document.querySelectorAll('.chapter-btn'));
  const chapterStarts = chapterBtns.map((b) => parseFloat(b.dataset.seek || '0'));

  function playMainAt(seconds) {
    if (!mainVideo) return;
    videoStage?.classList.add('is-playing');
    if (typeof seconds === 'number' && !Number.isNaN(seconds)) {
      mainVideo.currentTime = seconds;
    }
    mainVideo.play().catch(() => {
      // User can tap native video controls if playback requires a direct gesture
    });
  }

  posterPlay?.addEventListener('click', () => playMainAt());

  [heroPlayBtn, footerPlayBtn].forEach((btn) => {
    btn?.addEventListener('click', () => {
      document.getElementById('demo')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      playMainAt(0);
    });
  });

  mainVideo?.addEventListener('play', () => {
    videoStage?.classList.add('is-playing');
  });

  mainVideo?.addEventListener('timeupdate', () => {
    const t = mainVideo.currentTime;
    let activeIdx = 0;
    for (let i = 0; i < chapterStarts.length; i++) {
      if (t >= chapterStarts[i] - 0.15) activeIdx = i;
    }
    chapterBtns.forEach((btn, idx) => {
      btn.classList.toggle('is-active', idx === activeIdx);
    });
  });

  chapterBtns.forEach((btn) => {
    btn.addEventListener('click', () => {
      const seek = parseFloat(btn.dataset.seek || '0');
      playMainAt(seek);
    });
  });

  document.querySelectorAll('[data-jump-video]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const seek = parseFloat(btn.dataset.jumpVideo || '0');
      document.getElementById('demo')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      playMainAt(seek);
    });
  });

  // =========================================================================
  // 2. Top-Layer Dialog Exit Fallback + Light-Dismiss Fallback
  //    (modern-web-guidance: light-dismiss-a-dialog, animate-to-from-top-layer)
  // =========================================================================
  let supportsDisplayTransition;
  function canTransitionDisplay() {
    if (supportsDisplayTransition !== undefined) return supportsDisplayTransition;
    if (!window.CSS?.supports?.('transition-behavior', 'allow-discrete') || !document.body) {
      return false;
    }
    const probe = document.createElement('div');
    probe.style.cssText = 'transition: display 1s allow-discrete !important; display: block;';
    document.body.appendChild(probe);
    getComputedStyle(probe).display;
    probe.style.display = 'none';
    supportsDisplayTransition = getComputedStyle(probe).display === 'block';
    probe.remove();
    return supportsDisplayTransition;
  }

  function supportsTopLayerExit() {
    return Boolean(window.CSS?.supports?.('overlay', 'auto') && canTransitionDisplay());
  }

  async function closeTopLayer(dialog) {
    if (!dialog || !dialog.open) return;
    if (!supportsTopLayerExit()) {
      dialog.setAttribute('data-closing', '');
      const animations = dialog.getAnimations({ subtree: true });
      if (animations.length > 0) {
        await Promise.race([
          Promise.allSettled(animations.map((a) => a.finished)),
          new Promise((r) => setTimeout(r, 600)),
        ]);
      }
      if (!dialog.hasAttribute('data-closing')) return;
      dialog.removeAttribute('data-closing');
    }
    dialog.close();
  }

  function wireDialog(dialog) {
    if (!dialog) return;

    dialog.addEventListener('cancel', (event) => {
      event.preventDefault();
      closeTopLayer(dialog);
    });

    dialog.addEventListener('close', () => {
      dialog.removeAttribute('data-closing');
    });

    // Mandatory fallback for browsers without native <dialog closedby="any"> (e.g. Safari)
    if (!('closedBy' in HTMLDialogElement.prototype)) {
      dialog.addEventListener('click', (event) => {
        if (event.target !== dialog) return;
        const rect = dialog.getBoundingClientRect();
        const isDialogContent =
          rect.top <= event.clientY &&
          event.clientY <= rect.top + rect.height &&
          rect.left <= event.clientX &&
          event.clientX <= rect.left + rect.width;
        if (!isDialogContent) {
          closeTopLayer(dialog);
        }
      });
    }
  }

  document.querySelectorAll('[data-close-dialog]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const target = document.getElementById(btn.dataset.closeDialog);
      closeTopLayer(target);
    });
  });

  // App UI Screenshot Lightbox Modal
  const lightboxDialog = document.getElementById('lightbox-dialog');
  const lightboxImg = document.getElementById('lightbox-img');
  const lightboxCaption = document.getElementById('lightbox-caption');
  wireDialog(lightboxDialog);

  document.querySelectorAll('[data-lightbox]').forEach((btn) => {
    btn.addEventListener('click', () => {
      if (!lightboxDialog || !lightboxImg) return;
      lightboxImg.src = btn.dataset.lightbox;
      lightboxImg.alt = btn.dataset.caption || 'Gunnies Ballistics app screen';
      if (lightboxCaption) lightboxCaption.textContent = btn.dataset.caption || '';
      lightboxDialog.showModal();
    });
  });

  // =========================================================================
  // 3. Interactive Sample DOPE Table (MOA <-> MRAD toggle)
  // =========================================================================
  const DOPE_ROWS = [
    { yd: '25 yd', zero: false, elevMoa: -3.89, clicksMoa: 16, windMoa: 0.18, vel: 693, tof: '0.11s' },
    { yd: '50 yd (ZERO)', zero: true, elevMoa: 0.0, clicksMoa: 0, windMoa: 0.34, vel: 676, tof: '0.22s' },
    { yd: '75 yd', zero: false, elevMoa: 7.36, clicksMoa: 29, windMoa: 0.48, vel: 660, tof: '0.33s' },
    { yd: '100 yd', zero: false, elevMoa: 15.81, clicksMoa: 63, windMoa: 0.63, vel: 644, tof: '0.44s' },
    { yd: '125 yd', zero: false, elevMoa: 24.88, clicksMoa: 100, windMoa: 0.78, vel: 629, tof: '0.56s' },
    { yd: '150 yd', zero: false, elevMoa: 34.43, clicksMoa: 138, windMoa: 0.93, vel: 614, tof: '0.68s' },
    { yd: '175 yd', zero: false, elevMoa: 44.38, clicksMoa: 178, windMoa: 1.08, vel: 600, tof: '0.81s' },
  ];

  const MOA_TO_MRAD = 1 / 3.43774677;
  const tbody = document.getElementById('dope-tbody');
  const thElev = document.getElementById('th-elev');
  const thWind = document.getElementById('th-wind');
  const dopeTable = document.getElementById('dope-table');
  const unitBtns = Array.from(document.querySelectorAll('.unit-btn'));

  function renderDope(unit) {
    if (!tbody) return;
    const isMrad = unit === 'mrad';
    const uLabel = isMrad ? 'MRAD' : 'MOA';
    if (thElev) thElev.textContent = `Elevation (${uLabel})`;
    if (thWind) thWind.textContent = `Wind (${uLabel})`;
    dopeTable?.style.setProperty('--label-2', `"Elevation (${uLabel})"`);
    dopeTable?.style.setProperty('--label-4', `"Wind (${uLabel})"`);

    tbody.innerHTML = DOPE_ROWS.map((r) => {
      const elevVal = isMrad ? r.elevMoa * MOA_TO_MRAD : r.elevMoa;
      const windVal = isMrad ? r.windMoa * MOA_TO_MRAD : r.windMoa;
      const clicks = isMrad ? Math.round(Math.abs(elevVal) * 10) : r.clicksMoa;
      const elevStr =
        r.zero
          ? '0.00'
          : `${elevVal >= 0 ? 'U' : 'D'} ${Math.abs(elevVal).toFixed(2)}`;
      return `
        <tr class="${r.zero ? 'is-zero' : ''}">
          <th scope="row">${r.yd}</th>
          <td class="val-elev">${elevStr}</td>
          <td>${clicks}</td>
          <td class="val-wind">L ${windVal.toFixed(2)}</td>
          <td>${r.vel} fps</td>
          <td>${r.tof}</td>
        </tr>
      `;
    }).join('');
  }

  unitBtns.forEach((btn) => {
    btn.addEventListener('click', () => {
      const unit = btn.dataset.unit || 'moa';
      unitBtns.forEach((b) => {
        const active = b === btn;
        b.classList.toggle('is-active', active);
        b.setAttribute('aria-pressed', String(active));
      });
      renderDope(unit);
    });
  });

  renderDope('moa');

  // =========================================================================
  // 4. Scroll-Driven Reveal Fallback (IntersectionObserver when CSS unsupported)
  // =========================================================================
  if (
    !CSS.supports('(animation-timeline: view()) and (animation-range: entry)') &&
    window.matchMedia('(prefers-reduced-motion: no-preference)').matches
  ) {
    const items = document.querySelectorAll('.reveal-on-scroll');
    items.forEach((el) => {
      el.style.opacity = '0';
      el.style.transform = 'translateY(18px)';
      el.style.transition = 'opacity 0.45s ease-out, transform 0.45s ease-out';
    });

    const obs = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.style.opacity = '1';
            entry.target.style.transform = 'translateY(0)';
            obs.unobserve(entry.target);
          }
        }
      },
      { threshold: 0.12 }
    );
    items.forEach((el) => obs.observe(el));
  }
})();
