const root = document.getElementById('omok-friend-room-app');

if (root) {
  const stateUrl = root.dataset.stateUrl || '';
  const confirmUrl = root.dataset.confirmUrl || '';
  const shareUrl = root.dataset.shareUrl || '';
  const isHost = root.dataset.isHost === 'true';

  const statusPill = document.getElementById('omok-friend-status-pill');
  const statusText = document.getElementById('omok-friend-status-text');
  const confirmButton = document.getElementById('omok-friend-confirm');
  const confirmNote = document.getElementById('omok-friend-confirm-note');
  const shareInput = document.getElementById('omok-friend-share-url');
  const copyButton = document.getElementById('omok-friend-copy');

  let starting = false;

  const setStatus = state => {
    if (statusPill) {
      statusPill.textContent = state.started
        ? 'Room ready'
        : state.starting
          ? 'Starting room'
          : state.guestJoined
            ? 'Another player joined'
            : 'Waiting for another player';
      statusPill.classList.toggle('is-ready', !!state.guestJoined && !state.started && !state.starting);
      statusPill.classList.toggle('is-starting', !!state.starting || !!state.started);
    }

    if (statusText) {
      if (state.started) statusText.textContent = 'The omok room is ready. Redirecting now.';
      else if (state.starting) statusText.textContent = 'The room is starting. Stay on this page for a moment.';
      else if (state.isHost) {
        statusText.textContent = state.guestJoined
          ? 'The other player is here. Confirm the room to start the omok game.'
          : 'Stay on this page. The confirm button unlocks when another player opens the invite link.';
      } else {
        statusText.textContent = 'You joined the invite. Wait here until the host confirms the omok room.';
      }
    }

    if (confirmButton instanceof HTMLButtonElement) {
      confirmButton.disabled = !state.canConfirm || starting;
      confirmButton.textContent = starting || state.starting ? 'Starting...' : 'Confirm room';
    }

    if (confirmNote) {
      confirmNote.textContent = state.started
        ? 'Redirecting to the omok room.'
        : state.starting
          ? 'The host is creating the room.'
          : state.isHost
            ? state.guestJoined
              ? 'Ready to start.'
              : 'Waiting for another player.'
            : 'Only the host can confirm the room.';
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

  confirmButton?.addEventListener('click', async () => {
    if (!isHost || !confirmUrl || starting) return;
    starting = true;
    try {
      const res = await fetch(confirmUrl, {
        method: 'POST',
        credentials: 'same-origin',
      });
      const payload = await res.json().catch(() => ({}));
      if (res.ok && payload.redirectUrl) {
        location.href = payload.redirectUrl;
        return;
      }
      alert(String(payload.error || payload.message || 'Failed to create the omok room.'));
    } catch {
      alert('Failed to create the omok room.');
    } finally {
      starting = false;
      loadState().catch(() => {});
    }
  });

  if (shareInput instanceof HTMLInputElement) shareInput.value = shareUrl;
  loadState().catch(() => {});
  window.setInterval(() => {
    loadState().catch(() => {});
  }, 1200);
}
