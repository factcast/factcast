'use strict'

;(() => {
  if (typeof GLightbox !== 'function') return

  let trigger
  for (const image of document.querySelectorAll('.doc .imageblock img, .doc .image img')) {
    // Preserve authored links and let individual figures opt out of enlargement.
    if (image.closest('a, .no-lightbox')) continue
    const link = document.createElement('a')
    link.href = image.currentSrc || image.src
    link.className = 'image-lightbox'
    link.dataset.type = 'image'
    link.dataset.alt = image.alt
    link.setAttribute('aria-label', image.alt ? `Enlarge image: ${image.alt}` : 'Enlarge image')
    link.addEventListener('click', () => { trigger = link })
    image.replaceWith(link)
    link.appendChild(image)
  }
  if (!document.querySelector('a.image-lightbox')) return

  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const lightbox = GLightbox({
    selector: 'a.image-lightbox',
    closeButton: true,
    keyboardNavigation: true,
    touchNavigation: true,
    openEffect: reducedMotion ? 'none' : 'zoom',
    closeEffect: reducedMotion ? 'none' : 'zoom',
  })
  lightbox.on('open', () => {
    const dialog = document.getElementById('glightbox-body')
    dialog.setAttribute('aria-modal', 'true')
    dialog.setAttribute('aria-label', 'Image viewer')
    dialog.querySelector('.gclose').focus({ preventScroll: true })
    // Keep Tab navigation inside the modal, including when its Close button starts focused.
    dialog.addEventListener('keydown', (event) => {
      if (event.key !== 'Tab') return
      const controls = Array.from(dialog.querySelectorAll('button:not([disabled]), a[href], [tabindex="0"]'))
        .filter((control) => !control.classList.contains('disabled') && control.getClientRects().length > 0)
      const current = controls.indexOf(document.activeElement)
      const next = (current + (event.shiftKey ? -1 : 1) + controls.length) % controls.length
      event.preventDefault()
      event.stopPropagation()
      controls[next].focus()
    })
  })
  lightbox.on('close', () => trigger?.focus({ preventScroll: true }))
})()
