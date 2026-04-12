const root = document.getElementById('omok-friend-room-app');

if (root) {
  const stateUrl = root.dataset.stateUrl || '';
  const shareUrl = root.dataset.shareUrl || '';

  const statusPill = document.getElementById('omok-friend-status-pill');
  const statusText = document.getElementById('omok-friend-status-text');
  const confirmNote = document.getElementById('omok-friend-confirm-note');
  const shareInput = document.getElementById('omok-friend-share-url');
  const copyButton = document.getElementById('omok-friend-copy');

  const setStatus = state => {
    if (statusPill) {
      statusPill.textContent = state.started
        ? 'Room ready'
        : state.starting || state.guestJoined
          ? 'Starting room'
          : 'Waiting for another player';
      statusPill.classList.toggle('is-ready', !!state.guestJoined && !state.started && !state.starting);
      statusPill.classList.toggle('is-starting', !!state.starting || !!state.started || !!state.guestJoined);
    }

    if (statusText) {
      if (state.started) statusText.textContent = 'The omok room is ready. Redirecting now.';
      else if (state.starting) statusText.textContent = 'The room is starting automatically. Stay on this page for a moment.';
      else if (state.isHost) {
        statusText.textContent = state.guestJoined
          ? 'The other player opened the invite link. The omok room is starting automatically.'
          : 'Stay on this page. The first other player to open the invite link will be matched immediately.';
      } else {
        statusText.textContent = 'Joining the room. This should redirect automatically in a moment.';
      }
    }

    if (confirmNote) {
      confirmNote.textContent = state.started
        ? 'Redirecting to the omok room.'
        : state.starting || state.guestJoined
          ? 'Starting automatically.'
          : 'No extra confirm step.';
    }

    if (state.started && state.redirectUrl) location.href = state.redirectUrl;
  };

  const loadState = async () => {
    if (!stateUrl) return;
    const res = await fetch(stateUrl, { cache: 'no-store', credentials: 'same-origin' });
    if (!res.ok) return;
    const state = await res.json();
    setStatus(state);
  };

  copyButton?.addEventListener('click', async () => {
    try {
      if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(shareUrl);
      else if (shareInput instanceof HTMLInputElement) {
        shareInput.select();
        document.execCommand('copy');
      }
      if (copyButton) copyButton.textContent = 'Copied';
      window.setTimeout(() => {
        if (copyButton) copyButton.textContent = 'Copy link';
      }, 1200);
    } catch {
      if (copyButton) copyButton.textContent = 'Copy failed';
      window.setTimeout(() => {
        if (copyButton) copyButton.textContent = 'Copy link';
      }, 1200);
    }
  });

  if (shareInput instanceof HTMLInputElement) shareInput.value = shareUrl;
  loadState().catch(() => {});
  window.setInterval(() => {
    loadState().catch(() => {});
  }, 1200);
}
