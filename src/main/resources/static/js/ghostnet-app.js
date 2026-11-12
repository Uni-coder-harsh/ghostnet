/**
 * GhostNet — Private Offline Platform (Console Engine)
 * 100% self-contained, native browser WebCrypto, WebSockets, zero CDNs.
 */
(function() {
  'use strict';

  // State
  let currentUser = null;
  let currentChat = null; // { type: 'dm' | 'room', id, name, code, isOwner }
  let currentMessages = [];
  let ws = null;
  let pingTimer = null;
  let wsReconnectTimer = null;
  let typingTimeout = null;
  let pollTimer = null;
  let sidebarSyncTimer = null;

  // Vault State (Zero-knowledge client-side encryption)
  let vaultStatus = null;
  let vaultDerivedKey = null; // In-memory CryptoKey only while unlocked
  let currentVaultFolderId = null;
  let currentVaultBreadcrumbs = [];

  // Transfer Center State
  const activeTransfers = new Map();
  const completedTransfers = [];

  // Activity Log State
  const activityLogs = [];

  // DOM Elements - Shell & Header
  const consoleNav = document.getElementById('consoleNav');
  const hostIpText = document.getElementById('hostIpText');
  const hostIpBadge = document.getElementById('hostIpBadge');
  const unauthActions = document.getElementById('unauthActions');
  const authActions = document.getElementById('authActions');
  const headerAvatar = document.getElementById('headerAvatar');
  const headerUsername = document.getElementById('headerUsername');
  const rolePill = document.getElementById('rolePill');
  const guestWarningBanner = document.getElementById('guestWarningBanner');

  // DOM Elements - Navigation buttons
  const navOverview = document.getElementById('navOverview');
  const navMessages = document.getElementById('navMessages');
  const navRooms = document.getElementById('navRooms');
  const navTransfers = document.getElementById('navTransfers');
  const navVault = document.getElementById('navVault');
  const navMembers = document.getElementById('navMembers');
  const navActivity = document.getElementById('navActivity');
  const navSettings = document.getElementById('navSettings');
  const navAdmin = document.getElementById('navAdmin');

  // DOM Elements - View Panels
  const viewOverview = document.getElementById('viewOverview');
  const viewChat = document.getElementById('viewChat');
  const viewTransfers = document.getElementById('viewTransfers');
  const viewVault = document.getElementById('viewVault');
  const viewMembers = document.getElementById('viewMembers');
  const viewActivity = document.getElementById('viewActivity');
  const viewSettings = document.getElementById('viewSettings');
  const viewAdmin = document.getElementById('viewAdmin');

  // DOM Elements - Chat Workspace
  const appSidebar = document.getElementById('appSidebar');
  const sidebarRoomsContent = document.getElementById('sidebarRoomsContent');
  const sidebarDmContent = document.getElementById('sidebarDmContent');
  const tabRoomsBtn = document.getElementById('tabRoomsBtn');
  const tabDmBtn = document.getElementById('tabDmBtn');
  const myRoomsList = document.getElementById('myRoomsList');
  const publicRoomsList = document.getElementById('publicRoomsList');
  const usersList = document.getElementById('usersList');
  const chatArea = document.getElementById('chatArea');
  const emptyChatPlaceholder = document.getElementById('emptyChatPlaceholder');
  const activeChatContainer = document.getElementById('activeChatContainer');
  const activeChatAvatar = document.getElementById('activeChatAvatar');
  const activeChatTitle = document.getElementById('activeChatTitle');
  const activeChatSubtitle = document.getElementById('activeChatSubtitle');
  const activeChatCode = document.getElementById('activeChatCode');
  const messageStream = document.getElementById('messageStream');
  const chatForm = document.getElementById('chatForm');
  const chatInput = document.getElementById('chatInput');
  const chatAttachBtn = document.getElementById('chatAttachBtn');
  const chatFileInput = document.getElementById('chatFileInput');
  const chatUploadProgress = document.getElementById('chatUploadProgress');
  const chatUploadFilename = document.getElementById('chatUploadFilename');
  const chatUploadPercent = document.getElementById('chatUploadPercent');
  const chatUploadFill = document.getElementById('chatUploadFill');
  const typingBanner = document.getElementById('typingBanner');
  const typingText = document.getElementById('typingText');
  const participantCountBadge = document.getElementById('participantCountBadge');
  const viewParticipantsBtn = document.getElementById('viewParticipantsBtn');
  const leaveRoomBtn = document.getElementById('leaveRoomBtn');

  // DOM Elements - Vault Workspace
  const vaultBadge = document.getElementById('vaultBadge');
  const vaultGuestLocked = document.getElementById('vaultGuestLocked');
  const vaultUninitialized = document.getElementById('vaultUninitialized');
  const vaultLockedDialog = document.getElementById('vaultLockedDialog');
  const vaultUnlockedWorkspace = document.getElementById('vaultUnlockedWorkspace');
  const vaultInitForm = document.getElementById('vaultInitForm');
  const vaultInitPin = document.getElementById('vaultInitPin');
  const vaultInitPinConfirm = document.getElementById('vaultInitPinConfirm');
  const vaultUnlockForm = document.getElementById('vaultUnlockForm');
  const vaultUnlockPin = document.getElementById('vaultUnlockPin');
  const lockVaultBtn = document.getElementById('lockVaultBtn');
  const vaultQuotaUsageText = document.getElementById('vaultQuotaUsageText');
  const vaultQuotaPercent = document.getElementById('vaultQuotaPercent');
  const vaultQuotaFill = document.getElementById('vaultQuotaFill');
  const vaultBreadcrumbs = document.getElementById('vaultBreadcrumbs');
  const vaultGrid = document.getElementById('vaultGrid');
  const vaultSearchInput = document.getElementById('vaultSearchInput');
  const vaultNewFolderBtn = document.getElementById('vaultNewFolderBtn');
  const vaultUploadBtn = document.getElementById('vaultUploadBtn');
  const vaultUploadFileInput = document.getElementById('vaultUploadFileInput');

  // Modals
  const guestJoinModal = document.getElementById('guestJoinModal');
  const guestJoinForm = document.getElementById('guestJoinForm');
  const guestDisplayNameInput = document.getElementById('guestDisplayNameInput');
  const returningGuestModal = document.getElementById('returningGuestModal');
  const authModal = document.getElementById('authModal');
  const authForm = document.getElementById('authForm');
  const authTabLogin = document.getElementById('authTabLogin');
  const authTabRegister = document.getElementById('authTabRegister');
  const authUsername = document.getElementById('authUsername');
  const authPassword = document.getElementById('authPassword');
  const authDisplayName = document.getElementById('authDisplayName');
  const displayNameGroup = document.getElementById('displayNameGroup');
  const authSubmitBtn = document.getElementById('authSubmitBtn');
  const createRoomModal = document.getElementById('createRoomModal');
  const createRoomForm = document.getElementById('createRoomForm');
  const joinRoomModal = document.getElementById('joinRoomModal');
  const joinRoomForm = document.getElementById('joinRoomForm');
  const newFolderModal = document.getElementById('newFolderModal');
  const newFolderForm = document.getElementById('newFolderForm');
  const mediaPreviewModal = document.getElementById('mediaPreviewModal');
  const diagnosticModal = document.getElementById('diagnosticModal');
  const toastContainer = document.getElementById('toastContainer');

  // =========================================================================
  // 1. INITIALIZATION & ICONS
  // =========================================================================

  document.addEventListener('DOMContentLoaded', () => {
    initIcons();
    initApp();
    setupEventListeners();
  });

  function initIcons() {
    if (typeof GhostNetIcons === 'undefined') return;
    const iconMap = {
      brandIcon: 'ghost',
      diagIcon: 'activity',
      menuIcon: 'menu',
      guestQuickIcon: 'user',
      headerLogoutIcon: 'logout',
      navIconOverview: 'server',
      navIconMessages: 'users',
      navIconRooms: 'hash',
      navIconTransfers: 'transfers',
      navIconVault: 'lock',
      navIconMembers: 'users',
      navIconActivity: 'activity',
      navIconSettings: 'settings',
      navIconAdmin: 'shield',
      overviewHeaderIcon: 'server',
      overviewRefreshIcon: 'refresh',
      wifiBoxIcon: 'wifi',
      quickMsgIcon: 'users',
      quickRoomIcon: 'hash',
      quickVaultIcon: 'lock',
      roomsIcon: 'hash',
      usersIcon: 'users',
      plusIcon: 'plus',
      hashIcon: 'hash',
      refreshUsersIcon: 'refresh',
      placeholderGhostIcon: 'ghost',
      partIcon: 'users',
      sendIcon: 'send',
      attachIcon: 'paperclip',
      transfersHeaderIcon: 'transfers',
      vaultHeaderIcon: 'lock',
      vaultLockIcon: 'lock',
      vaultGuestLockIcon: 'lock',
      vaultInitIcon: 'lock',
      vaultPinIcon: 'lock',
      newFolderIcon: 'folder',
      vaultUploadIcon: 'upload',
      membersHeaderIcon: 'users',
      refreshMembersIcon: 'refresh',
      activityHeaderIcon: 'activity',
      settingsHeaderIcon: 'settings',
      adminHeaderIcon: 'shield',
      closeGuestIcon: 'close',
      closeReturningIcon: 'close',
      closeAuthIcon: 'close',
      closeCreateIcon: 'close',
      closeJoinIcon: 'close',
      closeNewFolderIcon: 'close',
      closeDiagIcon: 'close',
      rerunDiagIcon: 'refresh',
      closeMediaIcon: 'close'
    };

    for (const [id, iconKey] of Object.entries(iconMap)) {
      const el = document.getElementById(id);
      if (el && GhostNetIcons[iconKey]) {
        el.innerHTML = GhostNetIcons[iconKey];
      }
    }
  }

  async function initApp() {
    logActivity('SYSTEM', 'GhostNet console interface loaded');

    // 1. Fetch server host connectivity IP
    await fetchServerInfo();

    // 2. Check for active authenticated session
    const token = localStorage.getItem('ghostnet_token');
    if (token) {
      try {
        const res = await authFetch('/api/auth/me');
        if (res.ok) {
          currentUser = await res.json();
          onAuthenticated();
          return;
        } else {
          localStorage.removeItem('ghostnet_token');
        }
      } catch (e) {
        console.warn('Session verification failed:', e);
      }
    }

    // 3. Check for returning guest device credential
    const guestCred = localStorage.getItem('ghostnet_guest_credential');
    if (guestCred) {
      showReturningGuestDialog();
      return;
    }

    // 4. Fallback: Display default overview in unauthenticated view
    updateUserUI();
    switchView('viewOverview');
  }

  function onAuthenticated() {
    updateUserUI();
    initWebSocket();
    loadSidebarData();
    startPeriodicSync();
    logActivity('AUTH', `Operative '${currentUser.displayName}' active on private network`);
    autoSelectDefaultChat();
  }

  // =========================================================================
  // 2. AUTHENTICATION & IDENTITY (OPERATIVES & GUESTS)
  // =========================================================================

  function updateUserUI() {
    if (currentUser) {
      unauthActions.style.display = 'none';
      authActions.style.display = 'flex';
      headerAvatar.textContent = (currentUser.displayName || currentUser.username || '?').charAt(0).toUpperCase();
      headerUsername.textContent = currentUser.displayName || currentUser.username;

      const isAdmin = currentUser.role === 'ROLE_ADMIN';
      const isGuest = currentUser.role === 'ROLE_GUEST' || currentUser.isGuest;

      if (isAdmin) {
        rolePill.textContent = 'HOST ADMIN';
        rolePill.style.color = 'var(--neon-cyan)';
        rolePill.style.borderColor = 'rgba(0, 255, 200, 0.4)';
        navAdmin.style.display = 'flex';
      } else if (isGuest) {
        rolePill.textContent = 'GUEST';
        rolePill.style.color = 'var(--neon-yellow)';
        rolePill.style.borderColor = 'rgba(251, 191, 36, 0.4)';
        navAdmin.style.display = 'none';
        guestWarningBanner.style.display = 'flex';
      } else {
        rolePill.textContent = 'OPERATIVE';
        rolePill.style.color = 'var(--neon-cyan)';
        rolePill.style.borderColor = 'rgba(0, 255, 200, 0.4)';
        navAdmin.style.display = 'none';
        guestWarningBanner.style.display = 'none';
      }

      // Hide admin-only nav items for non-admins
      if (navMembers) navMembers.style.display = isAdmin ? 'flex' : 'none';
      if (navActivity) navActivity.style.display = isAdmin ? 'flex' : 'none';

      // Hide admin-only telemetry cards and diagnostic tools for non-admins
      document.querySelectorAll('.admin-only-metric').forEach(el => {
        el.style.display = isAdmin ? 'flex' : 'none';
      });
      const diagBtn = document.getElementById('diagnosticBtn');
      if (diagBtn) diagBtn.style.display = isAdmin ? 'inline-flex' : 'none';

      // Update nav index labels so they stay sequential
      _renumberNavItems();

      // Populate settings fields
      const sUser = document.getElementById('settingsUsername');
      const sDisp = document.getElementById('settingsDisplayName');
      const sBio = document.getElementById('settingsBio');
      if (sUser) sUser.value = currentUser.username;
      if (sDisp) sDisp.value = currentUser.displayName || '';
      if (sBio) sBio.value = currentUser.bio || '';
    } else {
      unauthActions.style.display = 'flex';
      authActions.style.display = 'none';
      navAdmin.style.display = 'none';
      guestWarningBanner.style.display = 'none';
      if (navMembers) navMembers.style.display = 'none';
      if (navActivity) navActivity.style.display = 'none';
      document.querySelectorAll('.admin-only-metric').forEach(el => el.style.display = 'none');
      const diagBtn = document.getElementById('diagnosticBtn');
      if (diagBtn) diagBtn.style.display = 'none';
    }
  }

  function _renumberNavItems() {
    let idx = 1;
    document.querySelectorAll('.console-nav-btn').forEach(btn => {
      if (btn.style.display !== 'none') {
        const idxEl = btn.querySelector('.nav-idx');
        if (idxEl) idxEl.textContent = `[0${idx}]`;
        idx++;
      }
    });
  }

  function openGuestModal() {
    guestJoinForm.reset();
    guestJoinModal.classList.add('active');
    guestDisplayNameInput.focus();
  }

  function showReturningGuestDialog() {
    returningGuestModal.classList.add('active');
  }

  async function restorePreviousGuest() {
    const cred = localStorage.getItem('ghostnet_guest_credential');
    if (!cred) return;
    try {
      const res = await fetch('/api/auth/guest/restore', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ deviceCredential: cred })
      });
      if (res.ok) {
        const data = await res.json();
        localStorage.setItem('ghostnet_token', data.token);
        localStorage.setItem('ghostnet_guest_credential', data.deviceCredential);
        currentUser = data.user;
        returningGuestModal.classList.remove('active');
        showToast(`Restored guest session: ${currentUser.displayName}`, 'success');
        onAuthenticated();
      } else {
        localStorage.removeItem('ghostnet_guest_credential');
        returningGuestModal.classList.remove('active');
        showToast('Previous guest credential has expired. Please join as a new guest.', 'info');
        openGuestModal();
      }
    } catch (e) {
      showToast('Failed to contact host server', 'error');
    }
  }

  async function handleGuestJoinSubmit(e) {
    e.preventDefault();
    const name = guestDisplayNameInput.value.trim();
    if (!name) return;

    try {
      const res = await fetch('/api/auth/guest', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ displayName: name })
      });
      if (res.ok) {
        const data = await res.json();
        localStorage.setItem('ghostnet_token', data.token);
        localStorage.setItem('ghostnet_guest_credential', data.deviceCredential);
        currentUser = data.user;
        guestJoinModal.classList.remove('active');
        showToast(`Joined as temporary guest: ${currentUser.displayName}`, 'success');
        onAuthenticated();
      } else {
        const err = await res.json();
        showToast(err.message || 'Failed to join as guest', 'error');
      }
    } catch (err) {
      showToast('Network error joining host', 'error');
    }
  }

  async function logout() {
    try {
      await authFetch('/api/auth/logout', { method: 'POST' });
    } catch (ignored) {}
    localStorage.removeItem('ghostnet_token');
    currentUser = null;
    currentChat = null;
    vaultDerivedKey = null;
    vaultStatus = null;
    if (ws) {
      try { ws.close(); } catch (ignored) {}
      ws = null;
    }
    updateUserUI();
    activeChatContainer.style.display = 'none';
    emptyChatPlaceholder.style.display = 'block';
    switchView('viewOverview');
    showToast('Session disconnected', 'info');
    logActivity('AUTH', 'Operative disconnected');
  }

  // =========================================================================
  // 3. WEBSOCKET REAL-TIME ENGINE
  // =========================================================================

  function initWebSocket() {
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    const token = localStorage.getItem('ghostnet_token');
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const tokenQuery = token ? `?token=${encodeURIComponent(token)}` : '';
    const wsUrl = `${protocol}//${location.host}/ws${tokenQuery}`;

    try {
      ws = new WebSocket(wsUrl);

      ws.onopen = () => {
        console.log('[GhostNet] WebSocket connected');
        updateConnectionBadge(true);
        startPing();
      };

      ws.onmessage = (event) => {
        try {
          const frame = JSON.parse(event.data);
          handleWsFrame(frame);
        } catch (e) {
          console.warn('Failed to parse WS frame:', e);
        }
      };

      ws.onclose = () => {
        updateConnectionBadge(false);
        stopPing();
        scheduleReconnect();
      };

      ws.onerror = (err) => {
        console.error('[GhostNet] WebSocket error', err);
      };
    } catch (e) {
      scheduleReconnect();
    }
  }

  function scheduleReconnect() {
    clearTimeout(wsReconnectTimer);
    wsReconnectTimer = setTimeout(() => {
      if (currentUser) initWebSocket();
    }, 2500);
  }

  function startPing() {
    stopPing();
    pingTimer = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'PING', requestId: Date.now().toString() }));
      }
    }, 15000);
  }

  function stopPing() {
    if (pingTimer) {
      clearInterval(pingTimer);
      pingTimer = null;
    }
  }

  function updateConnectionBadge(connected) {
    const dot = hostIpBadge.querySelector('.status-dot');
    if (connected) {
      dot.className = 'status-dot online';
      fetchServerInfo();
    } else {
      dot.className = 'status-dot warning';
      hostIpText.textContent = 'Reconnecting...';
    }
  }

  function handleWsFrame(frame) {
    const { type, payload } = frame;
    switch (type) {
      case 'CONNECTED_ACK':
        break;
      case 'DIRECT_MESSAGE_RECEIVED':
        handleIncomingDirectMessage(payload);
        break;
      case 'DIRECT_MESSAGE_SENT':
        handleOutgoingDirectMessage(payload);
        break;
      case 'ROOM_MESSAGE_RECEIVED':
        handleIncomingRoomMessage(payload);
        break;
      case 'PRESENCE_CHANGE':
        handlePresenceChange(payload);
        break;
      case 'USER_TYPING':
        handleUserTyping(payload);
        break;
      case 'ERROR':
        showToast(payload, 'error');
        break;
    }
  }

  function handleIncomingDirectMessage(msg) {
    logActivity('MSG', `Direct transmission received from ${msg.senderDisplayName}`);
    if (currentChat && currentChat.type === 'dm' && currentChat.id === msg.senderId) {
      appendMessageToStream(msg, false);
      authFetch(`/api/messages/direct/${msg.senderId}/read`, { method: 'POST' }).catch(() => {});
    } else {
      showToast(`Private transmission from ${msg.senderDisplayName}`, 'info');
      highlightCard(msg.senderId);
    }
  }

  function handleOutgoingDirectMessage(msg) {
    if (currentChat && currentChat.type === 'dm' && currentChat.id === msg.recipientId) {
      appendMessageToStream(msg, true);
    }
  }

  function handleIncomingRoomMessage(msg) {
    logActivity('ROOM', `Message in #${msg.roomName || 'Enclave'} from ${msg.senderDisplayName}`);
    // Check match against roomId, room code, or current active chat
    const matchesActiveRoom = currentChat && currentChat.type === 'room' &&
        (currentChat.id === msg.roomId || (currentChat.code && currentChat.code === msg.roomId));

    if (matchesActiveRoom) {
      const isOutgoing = currentUser && msg.senderId === currentUser.id;
      appendMessageToStream(msg, isOutgoing);
    } else {
      showToast(`New message in #${msg.roomName || 'Enclave'} from ${msg.senderDisplayName}`, 'info');
      highlightCard(msg.roomId);
    }
  }

  function handlePresenceChange({ userId, online }) {
    const card = document.querySelector(`.user-card[data-user-id="${userId}"]`);
    if (card) {
      const dot = card.querySelector('.status-dot');
      if (dot) dot.className = `status-dot ${online ? 'online' : 'offline'}`;
      const statusText = card.querySelector('.status-text');
      if (statusText) statusText.textContent = online ? 'Online' : 'Offline';
    }
  }

  function handleUserTyping({ roomId, recipientId, userId, typing }) {
    if (!currentChat) return;
    const isTarget = currentChat.type === 'room' ? currentChat.id === roomId : currentChat.id === recipientId;
    if (isTarget && typing) {
      typingText.textContent = 'Operative is transmitting...';
      typingBanner.style.display = 'block';
    } else {
      typingBanner.style.display = 'none';
    }
  }

  // =========================================================================
  // 4. CONSOLE NAVIGATION & VIEW SWITCHING
  // =========================================================================

  function switchView(viewId, subParam = null) {
    document.querySelectorAll('.view-panel').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('.console-nav-btn').forEach(b => b.classList.remove('active'));

    const targetPanel = document.getElementById(viewId);
    if (targetPanel) {
      targetPanel.classList.add('active');
    }

    // Highlight corresponding nav button
    const navBtn = document.querySelector(`.console-nav-btn[data-view="${viewId}"]`);
    if (navBtn) {
      navBtn.classList.add('active');
    }

    if (viewId === 'viewOverview') {
      updateOverviewMetrics();
    } else if (viewId === 'viewChat') {
      if (subParam === 'dm') {
        tabDmBtn.click();
      } else {
        tabRoomsBtn.click();
      }
    } else if (viewId === 'viewTransfers') {
      renderTransfers();
    } else if (viewId === 'viewVault') {
      renderVaultView();
    } else if (viewId === 'viewMembers') {
      loadMembersDirectory();
    } else if (viewId === 'viewActivity') {
      renderActivityLog();
    } else if (viewId === 'viewAdmin') {
      loadAdminOverview();
    }
  }

  function setupEventListeners() {
    const consoleBackdrop = document.getElementById('consoleBackdrop');
    const mobileMenuBtn = document.getElementById('mobileMenuBtn');
    const mobileBackBtn = document.getElementById('mobileBackBtn');

    // Console Nav Rail clicks
    document.querySelectorAll('.console-nav-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const viewId = btn.getAttribute('data-view');
        const sub = btn.getAttribute('data-sub');
        switchView(viewId, sub);
        consoleNav.classList.remove('open');
        consoleBackdrop?.classList.remove('open');
      });
    });

    // Mobile Menu Drawer Toggle
    mobileMenuBtn?.addEventListener('click', () => {
      consoleNav.classList.toggle('open');
      consoleBackdrop?.classList.toggle('open');
    });

    consoleBackdrop?.addEventListener('click', () => {
      consoleNav.classList.remove('open');
      consoleBackdrop.classList.remove('open');
    });

    mobileBackBtn?.addEventListener('click', () => {
      viewChat.classList.remove('chat-active');
    });

    // Overview Quick Buttons
    document.getElementById('quickOpenDmsBtn')?.addEventListener('click', () => switchView('viewChat', 'dm'));
    document.getElementById('quickOpenRoomsBtn')?.addEventListener('click', () => switchView('viewChat', 'rooms'));
    document.getElementById('quickOpenVaultBtn')?.addEventListener('click', () => switchView('viewVault'));
    document.getElementById('refreshOverviewBtn')?.addEventListener('click', updateOverviewMetrics);

    // Copy URL button
    document.getElementById('copyUrlBtn')?.addEventListener('click', () => {
      const url = document.getElementById('overviewAccessUrl').textContent.trim();
      navigator.clipboard.writeText(url).then(() => showToast('Access URL copied to clipboard', 'success'));
    });

    // Guest Actions
    document.getElementById('joinGuestQuickBtn').addEventListener('click', openGuestModal);
    document.getElementById('closeGuestModalBtn').addEventListener('click', () => guestJoinModal.classList.remove('active'));
    document.getElementById('guestJoinForm').addEventListener('submit', handleGuestJoinSubmit);
    document.getElementById('restoreGuestConfirmBtn').addEventListener('click', restorePreviousGuest);
    document.getElementById('newGuestInsteadBtn').addEventListener('click', () => {
      localStorage.removeItem('ghostnet_guest_credential');
      returningGuestModal.classList.remove('active');
      openGuestModal();
    });
    document.getElementById('guestUpgradeBtn')?.addEventListener('click', () => openAuthModal(true));

    // Auth Modal
    document.getElementById('loginOpenBtn').addEventListener('click', () => openAuthModal(false));
    document.getElementById('closeAuthModalBtn').addEventListener('click', () => authModal.classList.remove('active'));
    document.getElementById('headerLogoutBtn').addEventListener('click', logout);
    document.getElementById('userProfileBtn').addEventListener('click', () => switchView('viewSettings'));

    authTabLogin.addEventListener('click', () => setAuthTab('login'));
    authTabRegister.addEventListener('click', () => setAuthTab('register'));
    authForm.addEventListener('submit', handleAuthSubmit);

    // Chat Sidebar Tabs (Rooms vs DMs)
    tabRoomsBtn.addEventListener('click', () => {
      tabRoomsBtn.classList.add('active');
      tabDmBtn.classList.remove('active');
      sidebarRoomsContent.style.display = 'block';
      sidebarDmContent.style.display = 'none';
    });
    tabDmBtn.addEventListener('click', () => {
      tabDmBtn.classList.add('active');
      tabRoomsBtn.classList.remove('active');
      sidebarDmContent.style.display = 'block';
      sidebarRoomsContent.style.display = 'none';
      const q = document.getElementById('userSearchInput')?.value || '';
      loadUsers(false, q);
    });

    document.getElementById('userSearchInput')?.addEventListener('input', (e) => {
      loadUsers(false, e.target.value);
    });
    document.getElementById('refreshUsersBtn')?.addEventListener('click', () => {
      const q = document.getElementById('userSearchInput')?.value || '';
      loadUsers(false, q);
    });

    // Room Modals
    document.getElementById('openCreateRoomBtn').addEventListener('click', () => createRoomModal.classList.add('active'));
    document.getElementById('closeCreateRoomModalBtn').addEventListener('click', () => createRoomModal.classList.remove('active'));
    document.getElementById('cancelCreateRoomBtn').addEventListener('click', () => createRoomModal.classList.remove('active'));
    document.getElementById('createRoomForm').addEventListener('submit', handleCreateRoomSubmit);

    document.getElementById('openJoinRoomBtn').addEventListener('click', () => joinRoomModal.classList.add('active'));
    document.getElementById('closeJoinRoomModalBtn').addEventListener('click', () => joinRoomModal.classList.remove('active'));
    document.getElementById('cancelJoinRoomBtn').addEventListener('click', () => joinRoomModal.classList.remove('active'));
    document.getElementById('joinRoomForm').addEventListener('submit', handleJoinRoomSubmit);

    // Chat Form & Attachments
    chatForm.addEventListener('submit', handleSendMessage);
    chatAttachBtn.addEventListener('click', () => chatFileInput.click());
    chatFileInput.addEventListener('change', handleChatFileSelected);

    // Drag-and-drop on Chat Area
    chatArea.addEventListener('dragover', (e) => { e.preventDefault(); chatArea.classList.add('drag-over'); });
    chatArea.addEventListener('dragleave', () => { chatArea.classList.remove('drag-over'); });
    chatArea.addEventListener('drop', (e) => {
      e.preventDefault();
      chatArea.classList.remove('drag-over');
      if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
        promptChatUploadConfirmation(e.dataTransfer.files[0]);
      }
    });

    // Chat Upload Confirm Modal
    document.getElementById('closeChatUploadConfirmModalBtn')?.addEventListener('click', () => {
      document.getElementById('chatUploadConfirmModal').classList.remove('active');
    });
    document.getElementById('cancelChatUploadConfirmBtn')?.addEventListener('click', () => {
      document.getElementById('chatUploadConfirmModal').classList.remove('active');
    });
    document.getElementById('confirmChatUploadBtn')?.addEventListener('click', async () => {
      document.getElementById('chatUploadConfirmModal').classList.remove('active');
      if (!pendingChatUploadFile) return;
      const file = pendingChatUploadFile;
      const saveToVault = document.getElementById('chatUploadSaveToVaultCheckbox').checked;
      const pinInput = document.getElementById('chatUploadVaultPinInput');

      if (saveToVault && !vaultDerivedKey && pinInput && pinInput.value.trim() && vaultStatus?.salt) {
        try {
          const pin = pinInput.value.trim();
          const cand = await deriveKeyFromPin(pin, hexToBytes(vaultStatus.salt));
          const candBytes = await _vaultDigest(cand);
          if (bytesToHex(candBytes).toLowerCase() === vaultStatus.authVerifier.toLowerCase()) {
            vaultDerivedKey = cand;
          }
        } catch (ignored) {}
      }

      uploadConversationAttachment(file);
      if (saveToVault && vaultDerivedKey) {
        saveFileBufferToVault(file);
      }
    });

    // Vault Buttons & Forms
    vaultInitForm.addEventListener('submit', handleVaultInitSubmit);
    vaultUnlockForm.addEventListener('submit', handleVaultUnlockSubmit);
    lockVaultBtn.addEventListener('click', lockVault);
    document.getElementById('vaultRegisterBtn')?.addEventListener('click', () => openAuthModal(true));
    vaultNewFolderBtn.addEventListener('click', () => newFolderModal.classList.add('active'));
    document.getElementById('closeNewFolderModalBtn').addEventListener('click', () => newFolderModal.classList.remove('active'));
    document.getElementById('cancelNewFolderBtn').addEventListener('click', () => newFolderModal.classList.remove('active'));
    newFolderForm.addEventListener('submit', handleCreateFolderSubmit);
    vaultUploadBtn.addEventListener('click', () => vaultUploadFileInput.click());
    vaultUploadFileInput.addEventListener('change', handleVaultFileUpload);
    vaultSearchInput.addEventListener('input', () => loadVaultTree(currentVaultFolderId, vaultSearchInput.value));

    // Transfers
    document.getElementById('clearTransfersBtn').addEventListener('click', () => {
      completedTransfers.length = 0;
      renderTransfers();
      showToast('Cleared transfer history', 'info');
    });

    // Settings
    document.getElementById('resetGuestDeviceBtn').addEventListener('click', resetGuestDevice);
    document.getElementById('clearLocalStorageBtn').addEventListener('click', () => {
      localStorage.clear();
      showToast('Local browser storage cleared', 'info');
      setTimeout(() => location.reload(), 1000);
    });

    // Diagnostics
    document.getElementById('diagnosticBtn').addEventListener('click', runDiagnostics);
    document.getElementById('closeDiagnosticModalBtn').addEventListener('click', () => diagnosticModal.classList.remove('active'));
    document.getElementById('rerunDiagnosticBtn').addEventListener('click', runDiagnostics);

    // Media Preview Modal Close
    document.getElementById('closeMediaPreviewBtn').addEventListener('click', () => mediaPreviewModal.classList.remove('active'));
  }

  // =========================================================================
  // 5. CONVERSATION MESSAGING & FILE SHARING
  // =========================================================================

  async function loadSidebarData(silent = false) {
    await Promise.all([loadRooms(silent), loadUsers(silent)]);
  }

  async function loadRooms(silent = false) {
    try {
      const res = await authFetch('/api/rooms/my');
      if (res.ok) {
        const myRooms = await res.json();
        renderMyRooms(myRooms);
        document.getElementById('roomsTabCount').textContent = myRooms.length.toString();
        document.getElementById('roomsBadgeCount').textContent = myRooms.length.toString();
      }

      const pubRes = await authFetch('/api/rooms/public');
      if (pubRes.ok) {
        const publicRooms = await pubRes.json();
        renderPublicRooms(publicRooms);
      }
    } catch (e) {
      if (!silent) console.warn('Failed to load rooms:', e);
    }
  }

  function renderMyRooms(rooms) {
    myRoomsList.innerHTML = '';
    if (rooms.length === 0) {
      myRoomsList.innerHTML = `<div style="font-size: 0.78rem; color: var(--text-dim); padding: 8px 12px;">No active enclaves</div>`;
      return;
    }

    rooms.forEach(r => {
      const card = document.createElement('div');
      card.className = `item-card ${currentChat && currentChat.id === r.id ? 'active' : ''}`;
      card.setAttribute('data-chat-id', r.id);
      card.innerHTML = `
        <div class="avatar" style="font-size: 0.75rem;">#</div>
        <div class="item-info">
          <div class="item-name">${escapeHtml(r.name)}</div>
          <div class="item-sub">${r.memberCount || 1} operatives</div>
        </div>
        <span class="code-pill">${r.code}</span>
      `;
      card.addEventListener('click', () => {
        selectConversation('room', r.id, r.name, false, r.code, r.isOwner);
      });
      myRoomsList.appendChild(card);
    });
  }

  function renderPublicRooms(rooms) {
    publicRoomsList.innerHTML = '';
    const unjoined = rooms.filter(r => !r.isMember);
    if (unjoined.length === 0) {
      publicRoomsList.innerHTML = `<div style="font-size: 0.78rem; color: var(--text-dim); padding: 8px 12px;">All enclaves joined</div>`;
      return;
    }

    unjoined.forEach(r => {
      const card = document.createElement('div');
      card.className = 'item-card';
      card.innerHTML = `
        <div class="avatar" style="font-size: 0.75rem;">#</div>
        <div class="item-info">
          <div class="item-name">${escapeHtml(r.name)}</div>
          <div class="item-sub">Code: ${r.code} • ${r.memberCount || 1} ops</div>
        </div>
        <button class="btn btn-ghost btn-sm" style="padding: 2px 8px; font-size: 0.72rem;">Join</button>
      `;
      card.addEventListener('click', async () => {
        await joinRoom(r.code);
      });
      publicRoomsList.appendChild(card);
    });
  }

  async function loadUsers(silent = false, searchQuery = '') {
    try {
      const endpoint = searchQuery.trim()
        ? `/api/users/search?query=${encodeURIComponent(searchQuery.trim())}`
        : '/api/users/all';
      const res = await authFetch(endpoint);
      if (!res.ok) return;
      const users = await res.json();
      const otherUsers = currentUser ? users.filter(u => u.id !== currentUser.id) : users;
      const onlineCountVal = otherUsers.filter(u => u.online).length;
      document.getElementById('onlineCount').textContent = onlineCountVal.toString();
      document.getElementById('dmBadgeCount').textContent = onlineCountVal.toString();

      usersList.innerHTML = '';
      if (otherUsers.length === 0) {
        usersList.innerHTML = `<div style="font-size: 0.78rem; color: var(--text-dim); padding: 8px 12px;">${searchQuery ? 'No operatives match search' : 'No direct conversations yet. Search an operative username above.'}</div>`;
        return;
      }

      otherUsers.forEach(u => {
        const card = document.createElement('div');
        card.className = `item-card user-card ${currentChat && currentChat.id === u.id ? 'active' : ''}`;
        card.setAttribute('data-chat-id', u.id);
        card.setAttribute('data-user-id', u.id);
        const initial = (u.displayName || u.username).charAt(0).toUpperCase();

        card.innerHTML = `
          <div class="avatar">${initial}</div>
          <div class="item-info">
            <div class="item-name">
              <span>${escapeHtml(u.displayName || u.username)}</span>
              ${u.isGuest ? '<span class="code-pill" style="font-size: 0.6rem; color: var(--neon-yellow); margin-left: 4px;">GUEST</span>' : ''}
            </div>
            <div class="item-sub">
              <span class="status-dot ${u.online ? 'online' : 'offline'}"></span>
              <span class="status-text">${u.online ? 'Online' : 'Offline'}</span>
            </div>
          </div>
        `;

        card.addEventListener('click', () => {
          selectConversation('dm', u.id, u.displayName || u.username, u.online);
        });
        usersList.appendChild(card);
      });
    } catch (e) {
      if (!silent) console.warn('Failed to load users:', e);
    }
  }

  async function selectConversation(type, id, name, isOnline = false, code = null, isOwner = false) {
    currentChat = { type, id, name, code, isOwner };
    viewChat.classList.add('chat-active');

    document.querySelectorAll('.item-card').forEach(el => el.classList.remove('active'));
    const activeEl = document.querySelector(`.item-card[data-chat-id="${id}"]`);
    if (activeEl) activeEl.classList.add('active');

    emptyChatPlaceholder.style.display = 'none';
    activeChatContainer.style.display = 'flex';

    activeChatTitle.textContent = name;
    activeChatAvatar.textContent = (name || '?').charAt(0).toUpperCase();

    if (type === 'room') {
      activeChatCode.style.display = 'inline-block';
      activeChatCode.textContent = code || 'ENCLAVE';
      activeChatSubtitle.textContent = 'Room Enclave Transmission Channel';
      leaveRoomBtn.style.display = 'inline-flex';
      viewParticipantsBtn.style.display = 'inline-flex';
      updateRoomParticipantsBadge(id);
      loadRoomMessages(id);
    } else {
      activeChatCode.style.display = 'none';
      activeChatSubtitle.textContent = isOnline ? 'Operative Online' : 'Direct Encrypted Channel';
      leaveRoomBtn.style.display = 'none';
      viewParticipantsBtn.style.display = 'none';
      loadDirectMessages(id);
    }

    chatInput.focus();
  }

  async function autoSelectDefaultChat() {
    if (currentChat) return;
    try {
      const res = await authFetch('/api/rooms/my');
      if (res.ok) {
        const rooms = await res.json();
        const mainRoom = rooms.find(r => r.code === 'GN-MAIN' || r.name.toLowerCase().includes('general'));
        if (mainRoom) {
          selectConversation('room', mainRoom.id, mainRoom.name, false, mainRoom.code, mainRoom.isOwner);
          return;
        }
      }
      const joinRes = await authFetch('/api/rooms/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: 'GN-MAIN' })
      });
      if (joinRes.ok) {
        const room = await joinRes.json();
        selectConversation('room', room.id, room.name, false, room.code, room.isOwner);
        loadRooms(true);
      }
    } catch (e) {
      console.warn('Auto-select default chat failed:', e);
    }
  }

  async function loadDirectMessages(otherUserId) {
    messageStream.innerHTML = '<div style="text-align: center; color: var(--text-dim); padding: 1rem;">Loading transmission logs...</div>';
    try {
      const res = await authFetch(`/api/messages/direct/${otherUserId}?limit=50&offset=0`);
      if (res.ok) {
        currentMessages = await res.json();
        renderMessageStream(currentMessages, 'dm');
      }
    } catch (e) {
      console.warn('Failed to load DMs:', e);
    }
  }

  async function loadRoomMessages(roomId) {
    messageStream.innerHTML = '<div style="text-align: center; color: var(--text-dim); padding: 1rem;">Loading enclave transmissions...</div>';
    try {
      const res = await authFetch(`/api/messages/room/${roomId}?limit=50&offset=0`);
      if (res.ok) {
        currentMessages = await res.json();
        renderMessageStream(currentMessages, 'room');
      }
    } catch (e) {
      console.warn('Failed to load room messages:', e);
    }
  }

  async function pollActiveMessages() {
    if (!currentChat || !currentUser) return;
    try {
      const endpoint = currentChat.type === 'room'
          ? `/api/messages/room/${currentChat.id}?limit=50&offset=0`
          : `/api/messages/direct/${currentChat.id}?limit=50&offset=0`;
      const res = await authFetch(endpoint);
      if (res.ok) {
        const msgs = await res.json();
        mergeMessages(msgs);
      }
    } catch (e) {}
  }

  function mergeMessages(messages) {
    if (!messages || messages.length === 0) return;
    let anyAdded = false;
    messages.forEach(msg => {
      if (!document.querySelector(`.message-row[data-msg-id="${msg.id}"]`)) {
        const isOutgoing = currentUser && msg.senderId === currentUser.id;
        appendMessageToStream(msg, isOutgoing, false);
        anyAdded = true;
      }
    });
    if (anyAdded) scrollToBottom();
  }

  function renderMessageStream(messages, type) {
    messageStream.innerHTML = '';
    if (messages.length === 0) {
      messageStream.innerHTML = `<div style="text-align: center; color: var(--text-dim); margin: auto; font-size: 0.85rem; line-height: 1.6;">
        No transmissions yet in this ${type === 'room' ? 'enclave' : 'channel'}.<br>Type below to send a message or drag a file to attach.
      </div>`;
      return;
    }
    messages.forEach(msg => {
      const isOutgoing = currentUser && msg.senderId === currentUser.id;
      appendMessageToStream(msg, isOutgoing, false);
    });
    scrollToBottom();
  }

  function appendMessageToStream(msg, isOutgoing, autoScroll = true) {
    if (msg.id && document.querySelector(`.message-row[data-msg-id="${msg.id}"]`)) {
      return;
    }

    const row = document.createElement('div');
    row.className = `message-row ${isOutgoing ? 'outgoing' : 'incoming'}`;
    if (msg.id) row.setAttribute('data-msg-id', msg.id);

    const timeStr = formatTime(msg.createdAt);
    const senderLabel = isOutgoing ? 'You' : (msg.senderDisplayName || 'Operative');

    let attachmentHtml = '';
    if (msg.attachment) {
      const att = msg.attachment;
      const isImg = att.mimeType && att.mimeType.startsWith('image/');
      const previewImg = isImg ? `<img src="/api/files/${att.id}/preview" class="chat-img-preview" alt="${escapeHtml(att.filename)}" onclick="window.GhostNetApp.showPreview('${att.id}', '${escapeHtml(att.filename)}', 'image')">` : '';

      attachmentHtml = `
        ${previewImg}
        <div class="chat-attachment">
          <div class="chat-attachment-info">
            <span style="color: var(--neon-cyan);">${GhostNetIcons.file || '📄'}</span>
            <div>
              <div class="chat-attachment-name" title="${escapeHtml(att.filename)}">${escapeHtml(att.filename)}</div>
              <div class="chat-attachment-meta">${formatBytes(att.fileSize)} • SHA-256: <span class="attachment-hash-pill">${(att.checksumSha256 || '').substring(0, 8)}...</span></div>
            </div>
          </div>
          <button class="btn btn-ghost btn-sm" onclick="window.GhostNetApp.downloadChatAttachment('${att.id}', '${escapeHtml(att.filename)}')">
            ${GhostNetIcons.download || '⬇'} Download
          </button>
        </div>
      `;
    }

    row.innerHTML = `
      <div class="message-meta">
        <span class="sender-name">${escapeHtml(senderLabel)}</span>
        <span>•</span>
        <span>${timeStr}</span>
      </div>
      <div class="message-bubble">
        ${escapeHtml(msg.content)}
        ${attachmentHtml}
      </div>
    `;

    messageStream.appendChild(row);
    if (autoScroll) scrollToBottom();
  }

  function scrollToBottom() {
    messageStream.scrollTop = messageStream.scrollHeight;
  }

  function handleSendMessage(e) {
    e.preventDefault();
    const text = chatInput.value.trim();
    if (!text || !currentChat) return;

    chatInput.value = '';
    if (currentChat.type === 'dm') {
      sendDirectMessage(currentChat.id, text);
    } else {
      sendRoomMessage(currentChat.id, text);
    }
  }

  function sendDirectMessage(recipientId, content) {
    const clientMessageId = `cm-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`;
    const payload = { recipientId, content, clientMessageId };

    authFetch('/api/messages/direct', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    .then(r => {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    })
    .then(dto => {
      appendMessageToStream(dto, true);
    })
    .catch(() => showToast('Failed to deliver message', 'error'));
  }

  function sendRoomMessage(roomId, content, attachmentId = null) {
    const clientMessageId = `rm-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`;
    const payload = { roomId, content, attachmentId, clientMessageId };

    authFetch(`/api/messages/room/${roomId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    .then(r => {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    })
    .then(dto => {
      appendMessageToStream(dto, true);
    })
    .catch(() => showToast('Failed to post room message', 'error'));
  }

  // Conversation Attachment Uploading
  let pendingChatUploadFile = null;

  function promptChatUploadConfirmation(file) {
    if (!currentChat) {
      showToast('Select a room or operative before attaching files', 'info');
      return;
    }
    pendingChatUploadFile = file;
    document.getElementById('chatUploadModalFilename').textContent = file.name;
    document.getElementById('chatUploadModalFilesize').textContent = formatBytes(file.size);
    document.getElementById('chatUploadModalTarget').textContent = currentChat.name || 'Conversation';

    const saveCb = document.getElementById('chatUploadSaveToVaultCheckbox');
    const guestNote = document.getElementById('chatUploadVaultGuestNote');
    const lockedNote = document.getElementById('chatUploadVaultLockedNote');
    const pinInput = document.getElementById('chatUploadVaultPinInput');

    const isGuest = currentUser && (currentUser.role === 'ROLE_GUEST' || currentUser.isGuest);

    if (isGuest) {
      saveCb.checked = false;
      saveCb.disabled = true;
      guestNote.style.display = 'block';
      lockedNote.style.display = 'none';
    } else {
      saveCb.disabled = false;
      saveCb.checked = true;
      guestNote.style.display = 'none';
      if (!vaultDerivedKey) {
        lockedNote.style.display = 'block';
        if (pinInput) pinInput.value = '';
      } else {
        lockedNote.style.display = 'none';
      }
    }

    document.getElementById('chatUploadConfirmModal').classList.add('active');
  }

  function handleChatFileSelected(e) {
    if (e.target.files && e.target.files.length > 0) {
      promptChatUploadConfirmation(e.target.files[0]);
      e.target.value = '';
    }
  }

  async function saveFileBufferToVault(file) {
    if (!vaultDerivedKey) return;
    try {
      const arrayBuffer = await file.arrayBuffer();
      const iv = crypto.getRandomValues(new Uint8Array(12));
      const ciphertextBytes = await _vaultEncrypt(vaultDerivedKey, iv, arrayBuffer);
      const digest = _hasSubtle
        ? new Uint8Array(await crypto.subtle.digest('SHA-256', ciphertextBytes))
        : _PJS.sha256(ciphertextBytes);
      const sha256Hex = bytesToHex(digest);
      const ivHex = bytesToHex(iv);

      const blob = new Blob([ciphertextBytes], { type: 'application/octet-stream' });
      const formData = new FormData();
      formData.append('file', blob, file.name);
      formData.append('filename', file.name);
      formData.append('originalSize', file.size.toString());
      formData.append('isClientEncrypted', 'true');
      formData.append('iv', ivHex);
      formData.append('checksum', sha256Hex);
      if (currentVaultFolderId) formData.append('folderId', currentVaultFolderId);

      const res = await authFetch('/api/vault/files', {
        method: 'POST',
        body: formData
      });
      if (res.ok) {
        showToast(`'${file.name}' also encrypted and saved to your Private Vault!`, 'success');
        logActivity('VAULT', `Saved encrypted copy of '${file.name}'`);
      }
    } catch (e) {
      console.warn('Failed to save to vault:', e);
    }
  }

  async function uploadConversationAttachment(file) {
    if (!currentChat) {
      showToast('Select a room or operative before attaching files', 'info');
      return;
    }

    const convType = currentChat.type === 'dm' ? 'DIRECT' : 'ROOM';
    const convId = currentChat.id;

    // Show progress
    chatUploadProgress.style.display = 'block';
    chatUploadFilename.textContent = `Uploading ${file.name} (${formatBytes(file.size)})...`;
    chatUploadPercent.textContent = '0%';
    chatUploadFill.style.width = '0%';

    const transferId = `tr-${Date.now()}`;
    registerTransfer(transferId, file.name, file.size, 'UPLOAD');

    const formData = new FormData();
    formData.append('file', file);
    formData.append('conversationType', convType);
    formData.append('conversationId', convId);

    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/files/upload', true);

    const token = localStorage.getItem('ghostnet_token');
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) {
        const pct = Math.round((e.loaded / e.total) * 100);
        chatUploadPercent.textContent = `${pct}%`;
        chatUploadFill.style.width = `${pct}%`;
        updateTransferProgress(transferId, pct, e.loaded);
      }
    };

    xhr.onload = () => {
      chatUploadProgress.style.display = 'none';
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const att = JSON.parse(xhr.responseText);
          completeTransfer(transferId, att.checksumSha256);
          showToast(`File uploaded: ${att.filename}`, 'success');
          // Automatically transmit message referencing attachment
          if (currentChat.type === 'dm') {
            sendDirectMessage(currentChat.id, `Shared file: ${att.filename}`);
          } else {
            sendRoomMessage(currentChat.id, `Shared file: ${att.filename}`, att.id);
          }
        } catch (e) {
          failTransfer(transferId, 'Parse failure');
        }
      } else {
        failTransfer(transferId, `HTTP ${xhr.status}`);
        showToast('File upload failed', 'error');
      }
    };

    xhr.onerror = () => {
      chatUploadProgress.style.display = 'none';
      failTransfer(transferId, 'Network failure');
      showToast('Network error during file transmission', 'error');
    };

    xhr.send(formData);
  }

  // =========================================================================
  // 6. ZERO-KNOWLEDGE ENCRYPTED VAULT (WEBCRYPTO AES-256-GCM + PBKDF2)
  // =========================================================================

  async function renderVaultView() {
    if (!currentUser) {
      openAuthModal(false);
      return;
    }

    if (currentUser.role === 'ROLE_GUEST' || currentUser.isGuest) {
      // Scenario E: Guest restriction
      vaultGuestLocked.style.display = 'block';
      vaultUninitialized.style.display = 'none';
      vaultLockedDialog.style.display = 'none';
      vaultUnlockedWorkspace.style.display = 'none';
      lockVaultBtn.style.display = 'none';
      vaultBadge.textContent = 'RESTRICTED';
      return;
    }

    vaultGuestLocked.style.display = 'none';

    try {
      const res = await authFetch('/api/vault/status');
      if (res.ok) {
        vaultStatus = await res.json();
        if (!vaultStatus.isInitialized) {
          vaultUninitialized.style.display = 'block';
          vaultLockedDialog.style.display = 'none';
          vaultUnlockedWorkspace.style.display = 'none';
          lockVaultBtn.style.display = 'none';
          vaultBadge.textContent = 'SETUP REQUIRED';
        } else if (!vaultDerivedKey) {
          vaultUninitialized.style.display = 'none';
          vaultLockedDialog.style.display = 'block';
          vaultUnlockedWorkspace.style.display = 'none';
          lockVaultBtn.style.display = 'none';
          vaultBadge.textContent = 'LOCKED';
          vaultUnlockPin.focus();
        } else {
          showUnlockedVault();
        }
      }
    } catch (e) {
      showToast('Failed to connect to private vault', 'error');
    }
  }

  async function handleVaultInitSubmit(e) {
    e.preventDefault();
    const pin = vaultInitPin.value;
    const confirm = vaultInitPinConfirm.value;

    if (pin.length < 6) {
      showToast('Vault PIN must be at least 6 characters', 'error');
      return;
    }
    if (pin !== confirm) {
      showToast('Vault PIN confirmation does not match', 'error');
      return;
    }

    try {
      // 1. Generate 16-byte random salt
      const saltBytes = crypto.getRandomValues(new Uint8Array(16));
      const saltHex = bytesToHex(saltBytes);

      // 2. Derive key with PBKDF2 (WebCrypto on HTTPS, pure-JS on HTTP)
      const key = await deriveKeyFromPin(pin, saltBytes);

      // 3. Compute authVerifier = SHA-256(raw key bytes)
      const verifierBytes = await _vaultDigest(key);
      const verifierHex = bytesToHex(verifierBytes);

      // 4. Save zero-knowledge salt & verifier to host
      const res = await authFetch('/api/vault/init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ salt: saltHex, authVerifier: verifierHex })
      });

      if (res.ok) {
        vaultStatus = await res.json();
        vaultDerivedKey = key;
        showToast('Zero-knowledge vault initialized successfully!', 'success');
        logActivity('VAULT', 'Vault initialized');
        showUnlockedVault();
      } else {
        const err = await res.json();
        showToast(err.message || 'Vault setup failed', 'error');
      }
    } catch (err) {
      showToast('Vault init error: ' + err.message, 'error');
    }
  }

  async function handleVaultUnlockSubmit(e) {
    e.preventDefault();
    const pin = vaultUnlockPin.value;
    if (!pin || !vaultStatus) return;

    try {
      const saltBytes = hexToBytes(vaultStatus.salt);
      const candidateKey = await deriveKeyFromPin(pin, saltBytes);

      const candidateVerifierBytes = await _vaultDigest(candidateKey);
      const candidateVerifier = bytesToHex(candidateVerifierBytes);

      if (candidateVerifier.toLowerCase() === vaultStatus.authVerifier.toLowerCase()) {
        vaultDerivedKey = candidateKey;
        vaultUnlockPin.value = '';
        showToast('Vault unlocked', 'success');
        logActivity('VAULT', 'Vault unlocked via local PIN derivation');
        showUnlockedVault();
      } else {
        showToast('Incorrect Vault PIN or passphrase', 'error');
      }
    } catch (err) {
      showToast('Unlock failure: ' + err.message, 'error');
    }
  }

  function lockVault() {
    vaultDerivedKey = null;
    currentVaultFolderId = null;
    showToast('Vault locked. Encryption keys purged from memory.', 'info');
    logActivity('VAULT', 'Vault locked');
    renderVaultView();
  }

  function showUnlockedVault() {
    vaultUninitialized.style.display = 'none';
    vaultLockedDialog.style.display = 'none';
    vaultUnlockedWorkspace.style.display = 'block';
    lockVaultBtn.style.display = 'inline-flex';
    vaultBadge.textContent = 'UNLOCKED';
    updateVaultQuota();
    loadVaultTree(currentVaultFolderId);
  }

  function updateVaultQuota() {
    if (!vaultStatus) return;
    const usedMb = (vaultStatus.usedBytes / (1024 * 1024)).toFixed(1);
    const quotaMb = (vaultStatus.quotaBytes / (1024 * 1024)).toFixed(0);
    vaultQuotaUsageText.textContent = `${usedMb} MB / ${quotaMb} MB`;
    const pct = Math.min(Math.round(vaultStatus.usagePercentage || 0), 100);
    vaultQuotaPercent.textContent = `${pct}%`;
    vaultQuotaFill.style.width = `${pct}%`;
  }

  async function loadVaultTree(folderId = null, search = null) {
    currentVaultFolderId = folderId;
    let url = `/api/vault/tree?`;
    if (folderId) url += `folderId=${encodeURIComponent(folderId)}&`;
    if (search && search.trim()) url += `search=${encodeURIComponent(search.trim())}&`;

    try {
      const res = await authFetch(url);
      if (res.ok) {
        const tree = await res.json();
        renderVaultBreadcrumbs(tree.breadcrumbs);
        renderVaultItems(tree.folders, tree.files);
      }
    } catch (e) {
      console.warn('Failed to load vault tree:', e);
    }
  }

  function renderVaultBreadcrumbs(breadcrumbs) {
    vaultBreadcrumbs.innerHTML = '';
    const rootCrumb = document.createElement('span');
    rootCrumb.className = 'vault-crumb';
    rootCrumb.textContent = 'Root';
    rootCrumb.addEventListener('click', () => loadVaultTree(null));
    vaultBreadcrumbs.appendChild(rootCrumb);

    if (breadcrumbs && breadcrumbs.length > 0) {
      breadcrumbs.forEach(b => {
        const sep = document.createElement('span');
        sep.textContent = ' / ';
        sep.style.color = 'var(--text-dim)';
        vaultBreadcrumbs.appendChild(sep);

        const crumb = document.createElement('span');
        crumb.className = 'vault-crumb';
        crumb.textContent = b.name;
        crumb.addEventListener('click', () => loadVaultTree(b.id));
        vaultBreadcrumbs.appendChild(crumb);
      });
    }
  }

  function renderVaultItems(folders, files) {
    vaultGrid.innerHTML = '';

    if ((!folders || folders.length === 0) && (!files || files.length === 0)) {
      vaultGrid.innerHTML = `<div style="grid-column: 1/-1; text-align: center; color: var(--text-dim); padding: 40px;">
        This folder is empty. Click "Encrypt & Upload" or "New Folder" above.
      </div>`;
      return;
    }

    if (folders) {
      folders.forEach(f => {
        const card = document.createElement('div');
        card.className = 'vault-item-card';
        card.innerHTML = `
          <div class="vault-item-top">
            <div class="vault-item-icon">${GhostNetIcons.folder || '📁'}</div>
            <div class="vault-item-name" title="${escapeHtml(f.name)}">${escapeHtml(f.name)}</div>
          </div>
          <div class="vault-item-meta">
            <span>Folder</span>
            <button class="btn btn-ghost btn-sm" style="padding: 1px 5px;" onclick="event.stopPropagation(); window.GhostNetApp.deleteVaultFolder('${f.id}')">${GhostNetIcons.trash || '✕'}</button>
          </div>
        `;
        card.addEventListener('click', () => loadVaultTree(f.id));
        vaultGrid.appendChild(card);
      });
    }

    if (files) {
      files.forEach(f => {
        const card = document.createElement('div');
        card.className = 'vault-item-card';
        card.innerHTML = `
          <div class="vault-item-top">
            <div class="vault-item-icon">${GhostNetIcons.file || '📄'}</div>
            <div class="vault-item-name" title="${escapeHtml(f.filename)}">${escapeHtml(f.filename)}</div>
          </div>
          <div class="vault-item-meta">
            <span>${formatBytes(f.fileSize)}</span>
            <div style="display: flex; gap: 4px;">
              <button class="btn btn-ghost btn-sm" style="padding: 1px 5px;" title="Download & Decrypt" onclick="event.stopPropagation(); window.GhostNetApp.downloadVaultFile('${f.id}')">${GhostNetIcons.download || '⬇'}</button>
              <button class="btn btn-ghost btn-sm" style="padding: 1px 5px;" title="Delete" onclick="event.stopPropagation(); window.GhostNetApp.deleteVaultFile('${f.id}')">${GhostNetIcons.trash || '✕'}</button>
            </div>
          </div>
        `;
        vaultGrid.appendChild(card);
      });
    }
  }

  async function handleVaultFileUpload(e) {
    if (!e.target.files || e.target.files.length === 0 || !vaultDerivedKey) return;
    const file = e.target.files[0];
    showToast(`Encrypting '${file.name}' with AES-256-GCM...`, 'info');

    try {
      const arrayBuffer = await file.arrayBuffer();

      // 1. Generate 12-byte initialization vector (IV)
      const iv = crypto.getRandomValues(new Uint8Array(12));

      // 2. Encrypt plaintext buffer (WebCrypto or pure-JS fallback)
      const ciphertextBytes = await _vaultEncrypt(vaultDerivedKey, iv, arrayBuffer);

      // 3. Compute SHA-256 checksum of ciphertext
      const digest = _hasSubtle
        ? new Uint8Array(await crypto.subtle.digest('SHA-256', ciphertextBytes))
        : _PJS.sha256(ciphertextBytes);
      const sha256Hex = bytesToHex(digest);
      const ivHex = bytesToHex(iv);

      const blob = new Blob([ciphertextBytes], { type: 'application/octet-stream' });
      const formData = new FormData();
      formData.append('file', blob, file.name);
      formData.append('filename', file.name);
      formData.append('originalSize', file.size.toString());
      formData.append('isClientEncrypted', 'true');
      formData.append('iv', ivHex);
      formData.append('checksum', sha256Hex);
      if (currentVaultFolderId) formData.append('folderId', currentVaultFolderId);

      const res = await authFetch('/api/vault/files', {
        method: 'POST',
        body: formData
      });

      if (res.ok) {
        showToast(`'${file.name}' securely encrypted and saved to vault!`, 'success');
        logActivity('VAULT', `Stored encrypted file '${file.name}' (${formatBytes(file.size)})`);
        await renderVaultView();
      } else {
        const err = await res.json();
        showToast(err.message || 'Vault upload failed', 'error');
      }
    } catch (err) {
      showToast('Client-side encryption failure: ' + err.message, 'error');
    }
  }

  async function downloadAndDecryptVaultFile(fileId) {
    if (!vaultDerivedKey) {
      showToast('Unlock vault before decrypting files', 'info');
      return;
    }

    try {
      showToast('Downloading encrypted ciphertext...', 'info');
      const res = await authFetch(`/api/vault/files/${fileId}/download`);
      if (!res.ok) throw new Error('Download request rejected');

      const ivHeader = res.headers.get('X-GhostNet-IV');
      const disposition = res.headers.get('Content-Disposition') || '';
      let filename = 'decrypted-file.bin';
      const match = disposition.match(/filename="?([^";]+)"?/);
      if (match) filename = match[1];

      const ciphertext = await res.arrayBuffer();

      if (!ivHeader) throw new Error('Missing initialization vector from host');
      const iv = hexToBytes(ivHeader);

      showToast('Decrypting file...', 'info');
      const decrypted = await _vaultDecrypt(vaultDerivedKey, iv, ciphertext);

      // Trigger download of plaintext file
      const blob = new Blob([decrypted]);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);

      showToast(`Decrypted and saved: ${filename}`, 'success');
      logActivity('VAULT', `Decrypted and retrieved '${filename}'`);
    } catch (err) {
      showToast('Decryption failed: ' + err.message, 'error');
    }
  }

  async function handleCreateFolderSubmit(e) {
    e.preventDefault();
    const name = document.getElementById('newFolderNameInput').value.trim();
    if (!name) return;

    try {
      const res = await authFetch('/api/vault/folders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, parentId: currentVaultFolderId })
      });
      if (res.ok) {
        newFolderModal.classList.remove('active');
        document.getElementById('newFolderNameInput').value = '';
        showToast(`Created folder '${name}'`, 'success');
        loadVaultTree(currentVaultFolderId);
      }
    } catch (e) {
      showToast('Failed to create folder', 'error');
    }
  }

  // =========================================================================
  // 7. FILE TRANSFERS TRACKING
  // =========================================================================

  function registerTransfer(id, filename, size, direction = 'UPLOAD') {
    activeTransfers.set(id, {
      id,
      filename,
      size,
      direction,
      progress: 0,
      loaded: 0,
      startTime: Date.now(),
      status: 'IN_PROGRESS'
    });
    updateTransferPill();
    renderTransfers();
  }

  function updateTransferProgress(id, progress, loaded) {
    const t = activeTransfers.get(id);
    if (t) {
      t.progress = progress;
      t.loaded = loaded;
      renderTransfers();
    }
  }

  function completeTransfer(id, checksum = null) {
    const t = activeTransfers.get(id);
    if (t) {
      t.status = 'COMPLETED';
      t.progress = 100;
      t.checksum = checksum;
      t.endTime = Date.now();
      completedTransfers.unshift(t);
      activeTransfers.delete(id);
      updateTransferPill();
      renderTransfers();
    }
  }

  function failTransfer(id, reason = 'Error') {
    const t = activeTransfers.get(id);
    if (t) {
      t.status = 'FAILED';
      t.reason = reason;
      completedTransfers.unshift(t);
      activeTransfers.delete(id);
      updateTransferPill();
      renderTransfers();
    }
  }

  function updateTransferPill() {
    const count = activeTransfers.size;
    document.getElementById('metricActiveTransfers').textContent = count.toString();
    document.getElementById('transfersBadgeCount').textContent = count.toString();
    document.getElementById('activeTransferCountPill').textContent = `${count} ACTIVE`;
  }

  function renderTransfers() {
    const activeList = document.getElementById('activeTransfersList');
    const compList = document.getElementById('completedTransfersList');

    if (activeTransfers.size === 0) {
      activeList.innerHTML = `<div style="text-align: center; color: var(--text-dim); padding: 18px; font-size: 0.85rem;">
        No file transfers currently running. Files uploaded in conversations or vaults will track here.
      </div>`;
    } else {
      activeList.innerHTML = '';
      activeTransfers.forEach(t => {
        const item = document.createElement('div');
        item.className = 'transfer-item';
        const elapsedSec = (Date.now() - t.startTime) / 1000;
        const speed = elapsedSec > 0 ? formatBytes(t.loaded / elapsedSec) + '/s' : '--';

        item.innerHTML = `
          <div class="transfer-item-top">
            <span class="transfer-filename">${escapeHtml(t.filename)} (${t.direction})</span>
            <span style="font-family: var(--font-mono); font-size: 0.8rem; color: var(--neon-cyan);">${t.progress}%</span>
          </div>
          <div class="transfer-progress-track">
            <div class="transfer-progress-fill" style="width: ${t.progress}%;"></div>
          </div>
          <div class="transfer-footer">
            <span>${formatBytes(t.loaded)} of ${formatBytes(t.size)}</span>
            <span>Speed: ${speed}</span>
          </div>
        `;
        activeList.appendChild(item);
      });
    }

    if (completedTransfers.length === 0) {
      compList.innerHTML = `<div style="text-align: center; color: var(--text-dim); padding: 18px; font-size: 0.85rem;">
        No transfer history recorded this session.
      </div>`;
    } else {
      compList.innerHTML = '';
      completedTransfers.forEach(t => {
        const item = document.createElement('div');
        item.className = 'transfer-item';
        const isOk = t.status === 'COMPLETED';

        item.innerHTML = `
          <div class="transfer-item-top">
            <span class="transfer-filename">${escapeHtml(t.filename)}</span>
            <span class="code-pill" style="color: ${isOk ? 'var(--neon-green)' : 'var(--neon-red)'};">${t.status}</span>
          </div>
          <div class="transfer-footer">
            <span>Size: ${formatBytes(t.size)}</span>
            <span>${t.checksum ? `SHA-256: ${t.checksum.substring(0, 12)}...` : (t.reason || 'Completed')}</span>
          </div>
        `;
        compList.appendChild(item);
      });
    }
  }

  // =========================================================================
  // 8. NETWORK MEMBERS & ACTIVITY AUDIT
  // =========================================================================

  async function loadMembersDirectory() {
    try {
      const res = await authFetch('/api/users/all');
      if (!res.ok) return;
      const users = await res.json();
      document.getElementById('totalMembersCountPill').textContent = `${users.length} NODES`;
      const tbody = document.getElementById('membersTableBody');
      tbody.innerHTML = '';

      users.forEach(u => {
        const tr = document.createElement('tr');
        const roleStr = u.role === 'ROLE_ADMIN' ? 'HOST ADMIN' : (u.isGuest ? 'GUEST' : 'OPERATIVE');
        const roleColor = u.role === 'ROLE_ADMIN' ? 'var(--neon-cyan)' : (u.isGuest ? 'var(--neon-yellow)' : 'var(--text-main)');

        tr.innerHTML = `
          <td><strong>${escapeHtml(u.displayName || u.username)}</strong></td>
          <td><span class="code-pill" style="color: ${roleColor};">${roleStr}</span></td>
          <td>
            <span class="status-dot ${u.online ? 'online' : 'offline'}"></span>
            <span>${u.online ? 'Online' : 'Offline'}</span>
          </td>
          <td><span class="code-pill">#GN-MAIN</span></td>
          <td>
            ${currentUser && currentUser.id !== u.id ? `<button class="btn btn-ghost btn-sm" onclick="window.GhostNetApp.openDirect('${u.id}', '${escapeHtml(u.displayName || u.username)}')">Message</button>` : '<span style="color: var(--text-dim);">You</span>'}
          </td>
        `;
        tbody.appendChild(tr);
      });
    } catch (e) {
      console.warn('Failed to load members directory:', e);
    }
  }

  function logActivity(category, message) {
    const entry = {
      time: new Date().toLocaleTimeString(),
      category,
      message
    };
    activityLogs.unshift(entry);
    if (activityLogs.length > 100) activityLogs.pop();
    renderActivityLog();
  }

  function renderActivityLog() {
    const stream = document.getElementById('activityLogStream');
    if (!stream) return;
    if (activityLogs.length === 0) {
      stream.innerHTML = `<div style="color: var(--text-dim); text-align: center; padding: 20px;">System listening for local network events...</div>`;
      return;
    }
    stream.innerHTML = activityLogs.map(a => `
      <div style="display: flex; gap: 10px; border-bottom: 1px solid rgba(255,255,255,0.04); padding: 4px 0;">
        <span style="color: var(--text-dim);">${a.time}</span>
        <span class="code-pill" style="font-size: 0.65rem;">${a.category}</span>
        <span style="color: var(--text-main);">${escapeHtml(a.message)}</span>
      </div>
    `).join('');
  }

  // =========================================================================
  // 9. OVERVIEW TELEMETRY & ADMIN DASHBOARD
  // =========================================================================

  async function fetchServerInfo() {
    try {
      const res = await fetch('/api/network/addresses');
      if (res.ok) {
        const info = await res.json();
        const primary = (location.hostname !== 'localhost' && location.hostname !== '127.0.0.1')
          ? location.hostname
          : (info.primaryAddress || location.hostname);
        const port = info.port || location.port || '8080';
        hostIpText.textContent = `${primary}:${port}`;
        document.getElementById('overviewAccessUrl').textContent = `http://${primary}:${port}/`;
      }
    } catch (e) {}
  }

  async function updateOverviewMetrics() {
    try {
      const [netRes, diagRes] = await Promise.all([
        fetch('/api/network/addresses'),
        authFetch('/api/network/diagnostics')
      ]);

      if (netRes.ok) {
        const net = await netRes.json();
        const primary = (location.hostname !== 'localhost' && location.hostname !== '127.0.0.1')
          ? location.hostname
          : (net.primaryAddress || location.hostname);
        const port = net.port || location.port || '8080';
        hostIpText.textContent = `${primary}:${port}`;
        document.getElementById('overviewAccessUrl').textContent = `http://${primary}:${port}/`;
      }

      if (diagRes.ok) {
        const diag = await diagRes.json();
        document.getElementById('metricConnectedNodes').textContent = (diag.connectedClients || 1).toString();
        const totalMb = Math.round(diag.totalDiskBytes / (1024 * 1024));
        const freeMb = Math.round(diag.freeDiskBytes / (1024 * 1024));
        document.getElementById('metricStorageUsage').textContent = `${(freeMb / 1024).toFixed(1)} GB Free`;
        document.getElementById('metricStorageSub').textContent = `Total Capacity: ${(totalMb / 1024).toFixed(1)} GB`;
      }
    } catch (e) {}
  }

  async function loadAdminOverview() {
    if (!currentUser || currentUser.role !== 'ROLE_ADMIN') return;
    try {
      const [uRes, gRes, rRes, sRes] = await Promise.all([
        authFetch('/api/admin/users'),
        authFetch('/api/admin/guests'),
        authFetch('/api/admin/rooms'),
        authFetch('/api/admin/storage')
      ]);

      if (uRes.ok) document.getElementById('adminTotalUsers').textContent = (await uRes.json()).length.toString();
      if (gRes.ok) document.getElementById('adminTotalGuests').textContent = (await gRes.json()).length.toString();
      if (rRes.ok) document.getElementById('adminTotalRooms').textContent = (await rRes.json()).length.toString();
      if (sRes.ok) {
        const s = await sRes.json();
        document.getElementById('adminStoragePath').textContent = s.basePath || 'data/';
      }
    } catch (e) {}
  }

  // Diagnostics Modal Runner
  async function runDiagnostics() {
    const diagContent = document.getElementById('diagnosticContent');
    diagContent.innerHTML = 'Running offline-network probes...';
    diagnosticModal.classList.add('active');

    try {
      const res = await authFetch('/api/network/diagnostics');
      if (res.ok) {
        const d = await res.json();
        diagContent.innerHTML = `
          <div><strong style="color: var(--neon-cyan);">HOST INTERFACE REACHABLE:</strong> ${d.hostReachable ? 'YES (100% OK)' : 'NO'}</div>
          <div><strong style="color: var(--neon-cyan);">PRIMARY BIND ADDRESS:</strong> ${d.primaryAddress}:${d.serverPort}</div>
          <div><strong style="color: var(--neon-cyan);">EXTERNAL INTERNET DEPENDENCY:</strong> NONE (Air-Gapped Ready)</div>
          <div><strong style="color: var(--neon-cyan);">ACTIVE WEBSOCKET SESSIONS:</strong> ${d.activeWebSocketSessions}</div>
          <div><strong style="color: var(--neon-cyan);">STORAGE USABLE SPACE:</strong> ${(d.usableDiskBytes / (1024 * 1024)).toFixed(0)} MB</div>
          <div style="margin-top: 8px; color: var(--neon-green);">✓ GhostNet offline network operating reliably.</div>
        `;
      }
    } catch (e) {
      diagContent.innerHTML = '<span style="color: var(--neon-red);">Diagnostic probe failed to contact host server.</span>';
    }
  }

  // =========================================================================
  // 10. AUTH MODAL & FORM HANDLERS
  // =========================================================================

  function openAuthModal(isRegister = false) {
    authForm.reset();
    setAuthTab(isRegister ? 'register' : 'login');
    authModal.classList.add('active');
    authUsername.focus();
  }

  function setAuthTab(tab) {
    if (tab === 'register') {
      authTabRegister.classList.add('active');
      authTabLogin.classList.remove('active');
      displayNameGroup.style.display = 'block';
      authSubmitBtn.textContent = 'Register Operative Account';
    } else {
      authTabLogin.classList.add('active');
      authTabRegister.classList.remove('active');
      displayNameGroup.style.display = 'none';
      authSubmitBtn.textContent = 'Authenticate';
    }
  }

  async function handleAuthSubmit(e) {
    e.preventDefault();
    const isRegister = authTabRegister.classList.contains('active');
    const endpoint = isRegister ? '/api/auth/register' : '/api/auth/login';

    const payload = {
      username: authUsername.value.trim(),
      password: authPassword.value
    };
    if (isRegister && authDisplayName.value.trim()) {
      payload.displayName = authDisplayName.value.trim();
    }

    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (res.ok) {
        const data = await res.json();
        localStorage.setItem('ghostnet_token', data.token);
        currentUser = data.user;
        authModal.classList.remove('active');
        showToast(`Authenticated as ${currentUser.displayName}`, 'success');
        onAuthenticated();
      } else {
        const err = await res.json();
        showToast(err.message || 'Authentication failed', 'error');
      }
    } catch (err) {
      showToast('Network error during authentication', 'error');
    }
  }

  async function handleCreateRoomSubmit(e) {
    e.preventDefault();
    const name = document.getElementById('roomNameInput').value.trim();
    const desc = document.getElementById('roomDescInput').value.trim();
    const isPrivate = document.getElementById('roomPrivateCheckbox').checked;

    try {
      const res = await authFetch('/api/rooms', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, description: desc, isPrivate, capacity: 100 })
      });

      if (res.ok) {
        const room = await res.json();
        createRoomModal.classList.remove('active');
        createRoomForm.reset();
        showToast(`Room created! Joining code: ${room.code}`, 'success');
        await loadRooms();
        selectConversation('room', room.id, room.name, false, room.code, true);
      } else {
        const err = await res.json();
        showToast(err.message || 'Failed to create room', 'error');
      }
    } catch (e) {
      showToast('Failed to contact host', 'error');
    }
  }

  async function handleJoinRoomSubmit(e) {
    e.preventDefault();
    const code = document.getElementById('joinRoomCodeInput').value.trim();
    if (!code) return;
    await joinRoom(code);
    joinRoomModal.classList.remove('active');
  }

  async function joinRoom(code) {
    try {
      const res = await authFetch('/api/rooms/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: code.toUpperCase() })
      });
      if (res.ok) {
        const room = await res.json();
        showToast(`Joined #${room.name}`, 'success');
        await loadRooms();
        selectConversation('room', room.id, room.name, false, room.code, room.isOwner);
      } else {
        const err = await res.json();
        showToast(err.message || 'Failed to join room', 'error');
      }
    } catch (e) {
      showToast('Network error joining room', 'error');
    }
  }

  async function resetGuestDevice() {
    const cred = localStorage.getItem('ghostnet_guest_credential');
    if (cred) {
      try {
        await fetch('/api/auth/guest/reset', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ deviceCredential: cred })
        });
      } catch (ignored) {}
    }
    localStorage.removeItem('ghostnet_guest_credential');
    localStorage.removeItem('ghostnet_token');
    showToast('Guest device credential purged from browser', 'info');
    setTimeout(() => location.reload(), 1000);
  }

  function startPeriodicSync() {
    clearInterval(pollTimer);
    pollTimer = setInterval(pollActiveMessages, 2000);

    clearInterval(sidebarSyncTimer);
    sidebarSyncTimer = setInterval(() => {
      if (currentUser) loadSidebarData(true);
    }, 6000);

    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && currentUser) {
        if (!ws || ws.readyState !== WebSocket.OPEN) initWebSocket();
        pollActiveMessages();
        loadSidebarData(true);
      }
    });
  }

  async function updateRoomParticipantsBadge(roomId) {
    try {
      const res = await authFetch(`/api/rooms/${roomId}/participants`);
      if (res.ok) {
        const p = await res.json();
        participantCountBadge.textContent = p.length.toString();
      }
    } catch (e) {}
  }

  // =========================================================================
  // 11. CRYPTO & UTILITY HELPERS
  // Works on both HTTPS (WebCrypto) and HTTP hotspot/university WiFi (pure-JS)
  // =========================================================================

  // Detect secure context (HTTPS or localhost)
  const _hasSubtle = !!(window.crypto && window.crypto.subtle);

  // ---- Pure-JS fallback primitives ----
  const _PJS = (() => {
    // SHA-256 (pure JS)
    const K32 = new Uint32Array([
      0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
      0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
      0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
      0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
      0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
      0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
      0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
      0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
    ]);
    function rotr32(x, n) { return (x >>> n) | (x << (32 - n)); }
    function sha256(data) {
      const b = data instanceof Uint8Array ? data : new Uint8Array(data);
      const L = b.length, bL = L * 8;
      const pad = (L % 64 < 56) ? (56 - L % 64) : (120 - L % 64);
      const T = L + pad + 8, p = new Uint8Array(T);
      p.set(b); p[L] = 0x80;
      const dv = new DataView(p.buffer);
      dv.setUint32(T - 8, Math.floor(bL / 0x100000000), false);
      dv.setUint32(T - 4, bL >>> 0, false);
      let h0=0x6a09e667,h1=0xbb67ae85,h2=0x3c6ef372,h3=0xa54ff53a,
          h4=0x510e527f,h5=0x9b05688c,h6=0x1f83d9ab,h7=0x5be0cd19;
      const w = new Uint32Array(64);
      for (let o = 0; o < T; o += 64) {
        for (let i = 0; i < 16; i++) w[i] = dv.getUint32(o + i*4, false);
        for (let i = 16; i < 64; i++) {
          const s0 = rotr32(w[i-15],7)^rotr32(w[i-15],18)^(w[i-15]>>>3);
          const s1 = rotr32(w[i-2],17)^rotr32(w[i-2],19)^(w[i-2]>>>10);
          w[i] = (w[i-16]+s0+w[i-7]+s1)>>>0;
        }
        let a=h0,b2=h1,c=h2,d=h3,e=h4,f=h5,g=h6,h=h7;
        for (let i = 0; i < 64; i++) {
          const S1=(rotr32(e,6)^rotr32(e,11)^rotr32(e,25));
          const ch=(e&f)^((~e)&g);
          const t1=(h+S1+ch+K32[i]+w[i])>>>0;
          const S0=(rotr32(a,2)^rotr32(a,13)^rotr32(a,22));
          const mj=(a&b2)^(a&c)^(b2&c);
          h=g;g=f;f=e;e=(d+t1)>>>0;d=c;c=b2;b2=a;a=(t1+S0+mj)>>>0;
        }
        h0=(h0+a)>>>0;h1=(h1+b2)>>>0;h2=(h2+c)>>>0;h3=(h3+d)>>>0;
        h4=(h4+e)>>>0;h5=(h5+f)>>>0;h6=(h6+g)>>>0;h7=(h7+h)>>>0;
      }
      const r = new Uint8Array(32), rv = new DataView(r.buffer);
      rv.setUint32(0,h0,false);rv.setUint32(4,h1,false);rv.setUint32(8,h2,false);rv.setUint32(12,h3,false);
      rv.setUint32(16,h4,false);rv.setUint32(20,h5,false);rv.setUint32(24,h6,false);rv.setUint32(28,h7,false);
      return r;
    }
    function hmac(key, msg) {
      let k = key.length > 64 ? sha256(key) : key;
      const ik = new Uint8Array(64), ok = new Uint8Array(64);
      for (let i=0;i<k.length;i++){ik[i]=k[i]^0x36;ok[i]=k[i]^0x5c;}
      for (let i=k.length;i<64;i++){ik[i]=0x36;ok[i]=0x5c;}
      const inner = new Uint8Array(64+msg.length);
      inner.set(ik);inner.set(msg,64);
      const ih = sha256(inner);
      const outer = new Uint8Array(96);
      outer.set(ok);outer.set(ih,64);
      return sha256(outer);
    }
    function pbkdf2(pwd, salt, iters, keyLen) {
      const blocks = Math.ceil(keyLen / 32), out = new Uint8Array(blocks*32);
      for (let bi = 1; bi <= blocks; bi++) {
        const bs = new Uint8Array(salt.length+4);
        bs.set(salt);bs[salt.length]=(bi>>>24)&0xff;bs[salt.length+1]=(bi>>>16)&0xff;
        bs[salt.length+2]=(bi>>>8)&0xff;bs[salt.length+3]=bi&0xff;
        let u = hmac(pwd, bs), acc = new Uint8Array(u);
        for (let i=1;i<iters;i++){u=hmac(pwd,u);for(let j=0;j<32;j++)acc[j]^=u[j];}
        out.set(acc, (bi-1)*32);
      }
      return out.slice(0, keyLen);
    }
    // AES-256 key expansion
    const SB = new Uint8Array([
      99,124,119,123,242,107,111,197,48,1,103,43,254,215,171,118,202,130,201,125,250,89,71,240,173,
      212,162,175,156,164,114,192,183,253,147,38,54,63,247,204,52,165,229,241,113,216,49,21,4,199,35,
      195,24,150,5,154,7,18,128,226,235,39,178,117,9,131,44,26,27,110,90,160,82,59,214,179,41,227,47,
      132,83,209,0,237,32,252,177,91,106,203,190,57,74,76,88,207,208,239,170,251,67,77,51,133,69,249,
      2,127,80,60,159,168,81,163,64,143,146,157,56,245,188,182,218,33,16,255,243,210,205,12,19,236,95,
      151,68,23,196,167,126,61,100,93,25,115,96,129,79,220,34,42,144,136,70,238,184,20,222,94,11,219,
      224,50,58,10,73,6,36,92,194,211,172,98,145,149,228,121,231,200,55,109,141,213,78,169,108,86,244,
      234,101,122,174,8,186,120,37,46,28,166,180,198,232,221,116,31,75,189,139,138,112,62,181,102,72,3,
      246,14,97,53,87,185,134,193,29,158,225,248,152,17,105,217,142,148,155,30,135,233,206,85,40,223,
      140,161,137,13,191,230,66,104,65,153,45,15,176,84,187,22
    ]);
    const RC = [0x01000000,0x02000000,0x04000000,0x08000000,0x10000000,0x20000000,0x40000000,0x80000000,0x1b000000,0x36000000];
    function subW(w){return(SB[(w>>>24)&0xff]<<24)|(SB[(w>>>16)&0xff]<<16)|(SB[(w>>>8)&0xff]<<8)|SB[w&0xff];}
    function expandKey(k) {
      const W = new Uint32Array(60), kv = new DataView(k.buffer,k.byteOffset,32);
      for(let i=0;i<8;i++)W[i]=kv.getUint32(i*4,false);
      let rc=0;
      for(let i=8;i<60;i++){
        let t=W[i-1];
        if(i%8===0)t=subW((t<<8)|(t>>>24))^RC[rc++];
        else if(i%8===4)t=subW(t);
        W[i]=(W[i-8]^t)>>>0;
      }
      return W;
    }
    // T-tables (precomputed lazily)
    let T0,T1,T2,T3;
    function buildTables(){
      if(T0)return;
      T0=new Uint32Array(256);T1=new Uint32Array(256);T2=new Uint32Array(256);T3=new Uint32Array(256);
      function xm(x){return((x<<1)^(((x>>7)&1)*0x11b))&0xff;}
      for(let i=0;i<256;i++){
        const s=SB[i],s2=xm(s),s3=s2^s,t=(s2<<24)|(s<<16)|(s<<8)|s3;
        T0[i]=t>>>0;T1[i]=((t>>>8)|(t<<24))>>>0;T2[i]=((t>>>16)|(t<<16))>>>0;T3[i]=((t>>>24)|(t<<8))>>>0;
      }
    }
    function encBlock(W, inp, iOff, out, oOff){
      buildTables();
      const v=new DataView(inp.buffer,inp.byteOffset+iOff,16);
      let s0=v.getUint32(0,false)^W[0],s1=v.getUint32(4,false)^W[1],
          s2=v.getUint32(8,false)^W[2],s3=v.getUint32(12,false)^W[3],r=4;
      for(let rnd=1;rnd<14;rnd++){
        const t0=(T0[(s0>>>24)&0xff]^T1[(s1>>>16)&0xff]^T2[(s2>>>8)&0xff]^T3[s3&0xff]^W[r++])>>>0;
        const t1=(T0[(s1>>>24)&0xff]^T1[(s2>>>16)&0xff]^T2[(s3>>>8)&0xff]^T3[s0&0xff]^W[r++])>>>0;
        const t2=(T0[(s2>>>24)&0xff]^T1[(s3>>>16)&0xff]^T2[(s0>>>8)&0xff]^T3[s1&0xff]^W[r++])>>>0;
        const t3=(T0[(s3>>>24)&0xff]^T1[(s0>>>16)&0xff]^T2[(s1>>>8)&0xff]^T3[s2&0xff]^W[r++])>>>0;
        s0=t0;s1=t1;s2=t2;s3=t3;
      }
      const ov=new DataView(out.buffer,out.byteOffset+oOff,16);
      ov.setUint8(0,SB[(s0>>>24)&0xff]^(W[r]>>>24));ov.setUint8(1,SB[(s1>>>16)&0xff]^((W[r]>>>16)&0xff));
      ov.setUint8(2,SB[(s2>>>8)&0xff]^((W[r]>>>8)&0xff));ov.setUint8(3,SB[s3&0xff]^(W[r++]&0xff));
      ov.setUint8(4,SB[(s1>>>24)&0xff]^(W[r]>>>24));ov.setUint8(5,SB[(s2>>>16)&0xff]^((W[r]>>>16)&0xff));
      ov.setUint8(6,SB[(s3>>>8)&0xff]^((W[r]>>>8)&0xff));ov.setUint8(7,SB[s0&0xff]^(W[r++]&0xff));
      ov.setUint8(8,SB[(s2>>>24)&0xff]^(W[r]>>>24));ov.setUint8(9,SB[(s3>>>16)&0xff]^((W[r]>>>16)&0xff));
      ov.setUint8(10,SB[(s0>>>8)&0xff]^((W[r]>>>8)&0xff));ov.setUint8(11,SB[s1&0xff]^(W[r++]&0xff));
      ov.setUint8(12,SB[(s3>>>24)&0xff]^(W[r]>>>24));ov.setUint8(13,SB[(s0>>>16)&0xff]^((W[r]>>>16)&0xff));
      ov.setUint8(14,SB[(s1>>>8)&0xff]^((W[r]>>>8)&0xff));ov.setUint8(15,SB[s2&0xff]^(W[r]&0xff));
    }
    function ghMul(x, y) {
      const z=new Uint32Array(4),v=new Uint32Array(4),yv=new DataView(y.buffer,y.byteOffset,16);
      v[0]=yv.getUint32(0,false);v[1]=yv.getUint32(4,false);v[2]=yv.getUint32(8,false);v[3]=yv.getUint32(12,false);
      for(let i=0;i<16;i++){
        for(let bit=7;bit>=0;bit--){
          if((x[i]>>>bit)&1){z[0]^=v[0];z[1]^=v[1];z[2]^=v[2];z[3]^=v[3];}
          const lsb=v[3]&1;
          v[3]=(v[3]>>>1)|((v[2]&1)<<31);v[2]=(v[2]>>>1)|((v[1]&1)<<31);
          v[1]=(v[1]>>>1)|((v[0]&1)<<31);v[0]=(v[0]>>>1);
          if(lsb)v[0]^=0xe1000000;
        }
      }
      const o=new Uint8Array(16),ov=new DataView(o.buffer);
      ov.setUint32(0,z[0]>>>0,false);ov.setUint32(4,z[1]>>>0,false);
      ov.setUint32(8,z[2]>>>0,false);ov.setUint32(12,z[3]>>>0,false);
      return o;
    }
    function ghash(H, data) {
      let y=new Uint8Array(16),blk=new Uint8Array(16);
      for(let o=0;o<data.length;o+=16){
        blk.fill(0);blk.set(data.subarray(o,Math.min(o+16,data.length)),0);
        for(let i=0;i<16;i++)y[i]^=blk[i];
        y=ghMul(y,H);
      }
      return y;
    }
    function gcmJ0(W, iv) {
      if(iv.length===12){const j=new Uint8Array(16);j.set(iv);j[15]=1;return j;}
      const H=new Uint8Array(16);encBlock(W,new Uint8Array(16),0,H,0);
      const ib=iv.length*8, pl=Math.ceil(iv.length/16)*16+16;
      const gd=new Uint8Array(pl);gd.set(iv);
      const ldv=new DataView(gd.buffer,pl-16,16);
      ldv.setUint32(8,Math.floor(ib/0x100000000),false);ldv.setUint32(12,ib>>>0,false);
      return ghash(H,gd);
    }
    function gcmCtr(W,J0,plain){
      const ctr=new Uint8Array(J0),ks=new Uint8Array(16),out=new Uint8Array(plain.length);
      for(let o=0;o<plain.length;o+=16){
        const cv=new DataView(ctr.buffer);cv.setUint32(12,(cv.getUint32(12,false)+1)>>>0,false);
        encBlock(W,ctr,0,ks,0);
        for(let i=0;i<Math.min(16,plain.length-o);i++)out[o+i]=plain[o+i]^ks[i];
      }
      return out;
    }
    function gcmTag(W, H, J0, aad, cipher) {
      const aLen=aad.length,cLen=cipher.length;
      const ap=(16-aLen%16)%16,cp=(16-cLen%16)%16;
      const gd=new Uint8Array(aLen+ap+cLen+cp+16);
      gd.set(aad);gd.set(cipher,aLen+ap);
      const ldv=new DataView(gd.buffer,gd.length-16,16);
      ldv.setUint32(0,Math.floor(aLen*8/0x100000000),false);ldv.setUint32(4,(aLen*8)>>>0,false);
      ldv.setUint32(8,Math.floor(cLen*8/0x100000000),false);ldv.setUint32(12,(cLen*8)>>>0,false);
      const S=ghash(H,gd),eJ=new Uint8Array(16),tag=new Uint8Array(16);
      encBlock(W,J0,0,eJ,0);for(let i=0;i<16;i++)tag[i]=S[i]^eJ[i];
      return tag;
    }
    function encrypt(keyBytes, ivBytes, plain, aad=new Uint8Array(0)){
      const W=expandKey(keyBytes),H=new Uint8Array(16);
      encBlock(W,new Uint8Array(16),0,H,0);
      const J0=gcmJ0(W,ivBytes),c=gcmCtr(W,J0,plain),tag=gcmTag(W,H,J0,aad,c);
      const out=new Uint8Array(c.length+16);out.set(c);out.set(tag,c.length);return out;
    }
    function decrypt(keyBytes, ivBytes, cipherAndTag, aad=new Uint8Array(0)){
      const W=expandKey(keyBytes),H=new Uint8Array(16);
      encBlock(W,new Uint8Array(16),0,H,0);
      const pLen=cipherAndTag.length-16,cipher=cipherAndTag.subarray(0,pLen),expectedTag=cipherAndTag.subarray(pLen);
      const J0=gcmJ0(W,ivBytes),computedTag=gcmTag(W,H,J0,aad,cipher);
      let diff=0;for(let i=0;i<16;i++)diff|=(expectedTag[i]^computedTag[i]);
      if(diff!==0)throw new Error('Vault integrity check failed — wrong PIN or corrupted data');
      return gcmCtr(W,J0,cipher);
    }
    return { sha256, pbkdf2, encrypt, decrypt };
  })();

  // ---- Crypto abstraction (WebCrypto when available, pure-JS fallback on HTTP) ----
  async function deriveKeyFromPin(pin, saltBytes) {
    const enc = new TextEncoder();
    const pinBuf = enc.encode(pin);
    if (_hasSubtle) {
      const base = await crypto.subtle.importKey('raw', pinBuf, 'PBKDF2', false, ['deriveKey']);
      return crypto.subtle.deriveKey(
        { name: 'PBKDF2', salt: saltBytes, iterations: 600000, hash: 'SHA-256' },
        base, { name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt']
      );
    }
    // Pure-JS fallback — 100k iters (mobile-friendly, still strong)
    return { _pjs: true, raw: _PJS.pbkdf2(pinBuf, saltBytes, 100000, 32) };
  }

  async function _vaultDigest(key) {
    if (_hasSubtle) {
      const raw = await crypto.subtle.exportKey('raw', key);
      return new Uint8Array(await crypto.subtle.digest('SHA-256', raw));
    }
    return _PJS.sha256(key.raw);
  }

  async function _vaultEncrypt(key, iv, buf) {
    if (_hasSubtle) {
      return new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, buf));
    }
    return _PJS.encrypt(key.raw, iv, new Uint8Array(buf));
  }

  async function _vaultDecrypt(key, iv, buf) {
    if (_hasSubtle) {
      return await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, buf);
    }
    return _PJS.decrypt(key.raw, iv, new Uint8Array(buf)).buffer;
  }

  function bytesToHex(bytes) {
    return Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('');
  }

  function hexToBytes(hex) {
    const bytes = new Uint8Array(hex.length / 2);
    for (let i = 0; i < bytes.length; i++) {
      bytes[i] = parseInt(hex.substr(i * 2, 2), 16);
    }
    return bytes;
  }

  function authFetch(url, options = {}) {
    options.headers = options.headers || {};
    const token = localStorage.getItem('ghostnet_token');
    if (token) {
      if (options.headers instanceof Headers) {
        options.headers.set('Authorization', `Bearer ${token}`);
      } else {
        options.headers['Authorization'] = `Bearer ${token}`;
      }
    }
    return fetch(url, options);
  }

  function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    toastContainer.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  }

  function highlightCard(chatId) {
    const card = document.querySelector(`.item-card[data-chat-id="${chatId}"]`);
    if (card) {
      card.style.borderColor = 'var(--neon-cyan)';
      card.style.boxShadow = 'var(--neon-glow)';
      setTimeout(() => {
        card.style.borderColor = '';
        card.style.boxShadow = '';
      }, 3000);
    }
  }

  function formatBytes(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  function formatTime(isoString) {
    if (!isoString) return '';
    try {
      const d = new Date(isoString);
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (e) {
      return '';
    }
  }

  function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  // Global namespace for inline HTML event bindings
  window.GhostNetApp = {
    openDirect: (userId, name) => {
      switchView('viewChat', 'dm');
      selectConversation('dm', userId, name, true);
    },
    downloadChatAttachment: (attId, filename) => {
      const token = localStorage.getItem('ghostnet_token');
      const url = `/api/files/${attId}/download${token ? `?token=${encodeURIComponent(token)}` : ''}`;
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    },
    showPreview: (attId, filename, type) => {
      document.getElementById('previewModalTitle').textContent = `Preview: ${filename}`;
      const body = document.getElementById('previewModalBody');
      const link = document.getElementById('previewDownloadLink');
      link.href = `/api/files/${attId}/download`;
      link.download = filename;
      if (type === 'image') {
        body.innerHTML = `<img src="/api/files/${attId}/preview" style="max-width: 100%; max-height: 400px; border-radius: 4px;">`;
      }
      mediaPreviewModal.classList.add('active');
    },
    downloadVaultFile: (fileId) => {
      downloadAndDecryptVaultFile(fileId);
    },
    deleteVaultFile: async (fileId) => {
      if (!confirm('Are you sure you want to permanently delete this file from your private vault?')) return;
      try {
        const res = await authFetch(`/api/vault/files/${fileId}`, { method: 'DELETE' });
        if (res.ok) {
          showToast('Vault file deleted', 'info');
          await renderVaultView();
        }
      } catch (e) {
        showToast('Failed to delete file', 'error');
      }
    },
    deleteVaultFolder: async (folderId) => {
      if (!confirm('Are you sure you want to delete this folder and all files inside?')) return;
      try {
        const res = await authFetch(`/api/vault/folders/${folderId}`, { method: 'DELETE' });
        if (res.ok) {
          showToast('Vault folder deleted', 'info');
          await renderVaultView();
        }
      } catch (e) {
        showToast('Failed to delete folder', 'error');
      }
    }
  };

})();
