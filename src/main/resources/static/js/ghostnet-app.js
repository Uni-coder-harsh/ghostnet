/**
 * GhostNet v1.0.0 — Offline-First Real-Time SPA Application
 * 100% self-contained, native browser WebSockets, zero external CDNs (Req 011, 012, 041-048, 221-245).
 */
(function() {
  'use strict';

  // State
  let currentUser = null;
  let ws = null;
  let wsReconnectTimer = null;
  let pingTimer = null;
  let typingTimeout = null;
  let currentChat = null; // { type: 'dm' | 'room', id: string, name: string, code?: string }
  let currentMessages = [];
  let typingClearTimer = null;

  // DOM Elements
  const hostIpBadge = document.getElementById('hostIpBadge');
  const hostIpText = document.getElementById('hostIpText');
  const diagnosticBtn = document.getElementById('diagnosticBtn');
  const adminLink = document.getElementById('adminLink');
  const userProfileBtn = document.getElementById('userProfileBtn');
  const headerAvatar = document.getElementById('headerAvatar');
  const headerUsername = document.getElementById('headerUsername');
  const loginOpenBtn = document.getElementById('loginOpenBtn');

  const tabDmBtn = document.getElementById('tabDmBtn');
  const tabRoomsBtn = document.getElementById('tabRoomsBtn');
  const sidebarDmContent = document.getElementById('sidebarDmContent');
  const sidebarRoomsContent = document.getElementById('sidebarRoomsContent');
  const usersList = document.getElementById('usersList');
  const onlineCount = document.getElementById('onlineCount');
  const refreshUsersBtn = document.getElementById('refreshUsersBtn');

  const openCreateRoomBtn = document.getElementById('openCreateRoomBtn');
  const openJoinRoomBtn = document.getElementById('openJoinRoomBtn');
  const myRoomsList = document.getElementById('myRoomsList');
  const publicRoomsList = document.getElementById('publicRoomsList');

  const emptyChatPlaceholder = document.getElementById('emptyChatPlaceholder');
  const activeChatContainer = document.getElementById('activeChatContainer');
  const activeChatAvatar = document.getElementById('activeChatAvatar');
  const activeChatTitle = document.getElementById('activeChatTitle');
  const activeChatCode = document.getElementById('activeChatCode');
  const activeChatSubtitle = document.getElementById('activeChatSubtitle');
  const viewParticipantsBtn = document.getElementById('viewParticipantsBtn');
  const participantCountBadge = document.getElementById('participantCountBadge');
  const leaveRoomBtn = document.getElementById('leaveRoomBtn');
  const messageStream = document.getElementById('messageStream');
  const typingBanner = document.getElementById('typingBanner');
  const typingText = document.getElementById('typingText');
  const chatForm = document.getElementById('chatForm');
  const chatInput = document.getElementById('chatInput');

  // Modals
  const authModal = document.getElementById('authModal');
  const authForm = document.getElementById('authForm');
  const authTabLogin = document.getElementById('authTabLogin');
  const authTabRegister = document.getElementById('authTabRegister');
  const displayNameGroup = document.getElementById('displayNameGroup');
  const authSubmitBtn = document.getElementById('authSubmitBtn');
  const closeAuthModalBtn = document.getElementById('closeAuthModalBtn');

  const createRoomModal = document.getElementById('createRoomModal');
  const createRoomForm = document.getElementById('createRoomForm');
  const roomPrivateCheckbox = document.getElementById('roomPrivateCheckbox');
  const roomPasswordGroup = document.getElementById('roomPasswordGroup');
  const closeCreateRoomModalBtn = document.getElementById('closeCreateRoomModalBtn');
  const cancelCreateRoomBtn = document.getElementById('cancelCreateRoomBtn');

  const joinRoomModal = document.getElementById('joinRoomModal');
  const joinRoomForm = document.getElementById('joinRoomForm');
  const closeJoinRoomModalBtn = document.getElementById('closeJoinRoomModalBtn');
  const cancelJoinRoomBtn = document.getElementById('cancelJoinRoomBtn');

  const profileModal = document.getElementById('profileModal');
  const profileForm = document.getElementById('profileForm');
  const closeProfileModalBtn = document.getElementById('closeProfileModalBtn');
  const logoutBtn = document.getElementById('logoutBtn');

  const participantsModal = document.getElementById('participantsModal');
  const participantsList = document.getElementById('participantsList');
  const closeParticipantsModalBtn = document.getElementById('closeParticipantsModalBtn');

  const diagnosticModal = document.getElementById('diagnosticModal');
  const diagnosticContent = document.getElementById('diagnosticContent');
  const closeDiagnosticModalBtn = document.getElementById('closeDiagnosticModalBtn');
  const rerunDiagnosticBtn = document.getElementById('rerunDiagnosticBtn');

  const mobileMenuBtn = document.getElementById('mobileMenuBtn');
  const appSidebar = document.getElementById('appSidebar');

  // 1. Initialize Icons
  function injectIcons() {
    if (typeof GhostNetIcons === 'undefined') return;
    const map = {
      brandIcon: GhostNetIcons.ghost,
      menuIcon: GhostNetIcons.menu,
      diagIcon: GhostNetIcons.activity,
      shieldIcon: GhostNetIcons.shield,
      usersIcon: GhostNetIcons.users,
      roomsIcon: GhostNetIcons.hash,
      refreshUsersIcon: GhostNetIcons.refresh,
      plusIcon: GhostNetIcons.plus,
      hashIcon: GhostNetIcons.hash,
      placeholderGhostIcon: GhostNetIcons.ghost,
      partIcon: GhostNetIcons.users,
      sendIcon: GhostNetIcons.send,
      closeAuthIcon: GhostNetIcons.close,
      closeCreateIcon: GhostNetIcons.close,
      closeJoinIcon: GhostNetIcons.close,
      closeProfileIcon: GhostNetIcons.close,
      closePartIcon: GhostNetIcons.close,
      closeDiagIcon: GhostNetIcons.close,
      rerunDiagIcon: GhostNetIcons.refresh,
      logoutIcon: GhostNetIcons.logout
    };
    for (const [id, svg] of Object.entries(map)) {
      const el = document.getElementById(id);
      if (el) el.innerHTML = svg;
    }
  }

  // Toast Notification Engine
  function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      toast.style.transition = 'all 0.2s';
      setTimeout(() => toast.remove(), 250);
    }, 3500);
  }

  // 2. Auth Flow
  async function checkAuth() {
    try {
      const res = await fetch('/api/auth/me');
      if (res.ok) {
        currentUser = await res.json();
        updateUserUI();
        initWebSocket();
        loadSidebarData();
      } else {
        openAuthModal(false);
      }
    } catch (e) {
      openAuthModal(false);
    }
  }

  function updateUserUI() {
    if (currentUser) {
      loginOpenBtn.style.display = 'none';
      userProfileBtn.style.display = 'flex';
      headerAvatar.textContent = (currentUser.displayName || currentUser.username).charAt(0).toUpperCase();
      headerUsername.textContent = currentUser.displayName || currentUser.username;

      if (currentUser.role === 'ROLE_ADMIN') {
        adminLink.style.display = 'inline-flex';
      } else {
        adminLink.style.display = 'none';
      }
    } else {
      loginOpenBtn.style.display = 'inline-flex';
      userProfileBtn.style.display = 'none';
      adminLink.style.display = 'none';
    }
  }

  function openAuthModal(isRegister = false) {
    authModal.classList.add('active');
    if (isRegister) {
      authTabRegister.classList.add('active');
      authTabLogin.classList.remove('active');
      displayNameGroup.style.display = 'flex';
      authSubmitBtn.textContent = 'Create Offline Account';
    } else {
      authTabLogin.classList.add('active');
      authTabRegister.classList.remove('active');
      displayNameGroup.style.display = 'none';
      authSubmitBtn.textContent = 'Authenticate';
    }
  }

  // 3. WebSocket Real-Time Connection
  function initWebSocket() {
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${location.host}/ws`;

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
        console.warn('Failed to parse frame:', e);
      }
    };

    ws.onclose = () => {
      console.warn('[GhostNet] WebSocket disconnected. Reconnecting...');
      updateConnectionBadge(false);
      stopPing();
      scheduleReconnect();
    };

    ws.onerror = (err) => {
      console.error('[GhostNet] WebSocket transport error', err);
    };
  }

  function scheduleReconnect() {
    clearTimeout(wsReconnectTimer);
    wsReconnectTimer = setTimeout(() => {
      if (currentUser) initWebSocket();
    }, 3000);
  }

  function startPing() {
    stopPing();
    pingTimer = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'PING', requestId: Date.now().toString() }));
      }
    }, 20000);
  }

  function stopPing() {
    clearInterval(pingTimer);
  }

  function updateConnectionBadge(connected) {
    const dot = hostIpBadge.querySelector('.status-dot');
    if (connected) {
      dot.className = 'status-dot online';
      hostIpText.textContent = `${location.hostname}:${location.port || (location.protocol === 'https:' ? 443 : 8080)}`;
    } else {
      dot.className = 'status-dot warning';
      hostIpText.textContent = 'Reconnecting...';
    }
  }

  // 4. WebSocket Event Dispatcher
  function handleWsFrame(frame) {
    const { type, payload } = frame;

    switch (type) {
      case 'CONNECTED_ACK':
        console.log('[GhostNet] Connected ACK:', payload);
        break;

      case 'PONG':
        // Heartbeat response
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

      case 'MESSAGE_READ_RECEIPT':
        handleReadReceipt(payload);
        break;

      case 'ERROR':
        showToast(payload, 'error');
        break;
    }
  }

  function handleIncomingDirectMessage(msg) {
    if (currentChat && currentChat.type === 'dm' && currentChat.id === msg.senderId) {
      appendMessageToStream(msg, false);
      // Mark as read immediately
      fetch(`/api/messages/direct/${msg.senderId}/read`, { method: 'POST' }).catch(() => {});
    } else {
      showToast(`New message from ${msg.senderDisplayName}`, 'info');
      highlightUserCard(msg.senderId);
    }
  }

  function handleOutgoingDirectMessage(msg) {
    if (currentChat && currentChat.type === 'dm' && currentChat.id === msg.recipientId) {
      appendMessageToStream(msg, true);
    }
  }

  function handleIncomingRoomMessage(msg) {
    if (currentChat && currentChat.type === 'room' && currentChat.id === msg.roomId) {
      const isOutgoing = currentUser && msg.senderId === currentUser.id;
      appendMessageToStream(msg, isOutgoing);
    }
  }

  function handlePresenceChange({ userId, online }) {
    const userCard = document.querySelector(`.user-card[data-user-id="${userId}"]`);
    if (userCard) {
      const dot = userCard.querySelector('.online-indicator');
      if (dot) {
        dot.className = `online-indicator ${online ? '' : 'offline'}`;
      }
      const sub = userCard.querySelector('.item-sub');
      if (sub) {
        sub.textContent = online ? 'Online now' : 'Offline';
      }
    }
    updateOnlineUserCount();
  }

  function handleUserTyping({ userId, roomId, typing }) {
    if (!currentChat) return;
    if (currentChat.type === 'dm' && currentChat.id === userId && typing) {
      typingText.textContent = `${currentChat.name} is typing...`;
      typingBanner.style.display = 'flex';
      clearTimeout(typingClearTimer);
      typingClearTimer = setTimeout(() => { typingBanner.style.display = 'none'; }, 3000);
    } else if (currentChat.type === 'room' && currentChat.id === roomId && typing) {
      typingText.textContent = 'Someone is typing in this room...';
      typingBanner.style.display = 'flex';
      clearTimeout(typingClearTimer);
      typingClearTimer = setTimeout(() => { typingBanner.style.display = 'none'; }, 3000);
    }
  }

  function handleReadReceipt({ readerId }) {
    if (currentChat && currentChat.type === 'dm' && currentChat.id === readerId) {
      document.querySelectorAll('.message-status').forEach(el => {
        el.textContent = '✓✓ Read';
      });
    }
  }

  // 5. Sidebar Loading
  async function loadSidebarData() {
    loadUsers();
    loadRooms();
  }

  async function loadUsers() {
    try {
      const res = await fetch('/api/users/all');
      if (!res.ok) return;
      const users = await res.json();
      renderUsers(users);
    } catch (e) {
      console.warn('Failed to load users:', e);
    }
  }

  function renderUsers(users) {
    usersList.innerHTML = '';
    const otherUsers = users.filter(u => !currentUser || u.id !== currentUser.id);

    if (otherUsers.length === 0) {
      usersList.innerHTML = `<div style="text-align: center; padding: 2rem 1rem; color: var(--text-dim); font-size: 0.8rem;">
        No other operatives registered yet. Connect another device to your hotspot to communicate.
      </div>`;
      onlineCount.textContent = '0';
      return;
    }

    let onlineTotal = 0;
    otherUsers.forEach(u => {
      if (u.online) onlineTotal++;
      const card = document.createElement('div');
      card.className = `item-card user-card ${currentChat && currentChat.id === u.id ? 'active' : ''}`;
      card.setAttribute('data-user-id', u.id);
      const initial = (u.displayName || u.username).charAt(0).toUpperCase();

      card.innerHTML = `
        <div class="item-info">
          <div class="avatar">
            ${initial}
            <span class="online-indicator ${u.online ? '' : 'offline'}"></span>
          </div>
          <div class="item-details">
            <div class="item-name">${escapeHtml(u.displayName || u.username)}</div>
            <div class="item-sub">${u.online ? 'Online now' : 'Offline'}</div>
          </div>
        </div>
      `;

      card.addEventListener('click', () => {
        selectConversation('dm', u.id, u.displayName || u.username, u.online);
        if (window.innerWidth <= 768) {
          appSidebar.classList.remove('open');
        }
      });

      usersList.appendChild(card);
    });

    onlineCount.textContent = onlineTotal.toString();
  }

  function updateOnlineUserCount() {
    const dots = document.querySelectorAll('.user-card .online-indicator:not(.offline)');
    onlineCount.textContent = dots.length.toString();
  }

  async function loadRooms() {
    try {
      const [myRes, pubRes] = await Promise.all([
        fetch('/api/rooms/my'),
        fetch('/api/rooms/public')
      ]);

      if (myRes.ok) {
        const myRooms = await myRes.json();
        renderMyRooms(myRooms);
      }
      if (pubRes.ok) {
        const pubRooms = await pubRes.json();
        renderPublicRooms(pubRooms);
      }
    } catch (e) {
      console.warn('Failed to load rooms:', e);
    }
  }

  function renderMyRooms(rooms) {
    myRoomsList.innerHTML = '';
    if (rooms.length === 0) {
      myRoomsList.innerHTML = `<div style="padding: 0.5rem; color: var(--text-dim); font-size: 0.78rem;">You haven't joined any rooms yet.</div>`;
      return;
    }

    rooms.forEach(r => {
      const card = document.createElement('div');
      card.className = `item-card ${currentChat && currentChat.id === r.id ? 'active' : ''}`;
      card.innerHTML = `
        <div class="item-info">
          <div class="avatar" style="color: var(--neon-blue);">#</div>
          <div class="item-details">
            <div class="item-name">${escapeHtml(r.name)}</div>
            <div class="item-sub">${r.memberCount} members</div>
          </div>
        </div>
        <span class="code-pill">${r.code}</span>
      `;
      card.addEventListener('click', () => {
        selectConversation('room', r.id, r.name, false, r.code, r.isOwner);
        if (window.innerWidth <= 768) appSidebar.classList.remove('open');
      });
      myRoomsList.appendChild(card);
    });
  }

  function renderPublicRooms(rooms) {
    publicRoomsList.innerHTML = '';
    if (rooms.length === 0) {
      publicRoomsList.innerHTML = `<div style="padding: 0.5rem; color: var(--text-dim); font-size: 0.78rem;">No public rooms discovered.</div>`;
      return;
    }

    rooms.forEach(r => {
      const card = document.createElement('div');
      card.className = 'item-card';
      card.innerHTML = `
        <div class="item-info">
          <div class="avatar" style="color: var(--neon-cyan);">#</div>
          <div class="item-details">
            <div class="item-name">${escapeHtml(r.name)}</div>
            <div class="item-sub">${r.memberCount} / ${r.capacity} capacity</div>
          </div>
        </div>
        ${r.isMember
          ? `<span class="brand-badge">JOINED</span>`
          : `<button class="btn btn-primary btn-sm join-public-btn" data-code="${r.code}">Join</button>`
        }
      `;

      const joinBtn = card.querySelector('.join-public-btn');
      if (joinBtn) {
        joinBtn.addEventListener('click', async (e) => {
          e.stopPropagation();
          await joinRoom(r.code);
        });
      }

      card.addEventListener('click', () => {
        if (r.isMember) {
          selectConversation('room', r.id, r.name, false, r.code, r.isOwner);
          if (window.innerWidth <= 768) appSidebar.classList.remove('open');
        }
      });

      publicRoomsList.appendChild(card);
    });
  }

  // 6. Conversation Switching
  async function selectConversation(type, id, name, isOnline = false, code = null, isOwner = false) {
    currentChat = { type, id, name, code, isOwner };

    // Update Sidebar active state
    document.querySelectorAll('.item-card').forEach(el => el.classList.remove('active'));

    emptyChatPlaceholder.style.display = 'none';
    activeChatContainer.style.display = 'flex';

    activeChatTitle.textContent = name;
    activeChatAvatar.textContent = name.charAt(0).toUpperCase();

    if (type === 'dm') {
      activeChatCode.style.display = 'none';
      viewParticipantsBtn.style.display = 'none';
      leaveRoomBtn.style.display = 'none';
      activeChatSubtitle.textContent = isOnline ? 'Online now' : 'Offline (Messages stored persistently)';
      loadDirectMessages(id);
    } else {
      activeChatCode.style.display = 'inline-block';
      activeChatCode.textContent = code;
      viewParticipantsBtn.style.display = 'inline-flex';
      leaveRoomBtn.style.display = 'inline-flex';
      activeChatSubtitle.textContent = `Room Code: ${code} • Offline mesh broadcast`;
      loadRoomMessages(id);
      updateRoomParticipantsBadge(id);
    }

    chatInput.focus();
  }

  async function loadDirectMessages(otherUserId) {
    messageStream.innerHTML = '<div style="text-align: center; color: var(--text-dim); padding: 1rem;">Loading history...</div>';
    try {
      const res = await fetch(`/api/messages/direct/${otherUserId}?limit=50&offset=0`);
      if (res.ok) {
        currentMessages = await res.json();
        renderMessageStream(currentMessages, 'dm');
        // Mark conversation read
        fetch(`/api/messages/direct/${otherUserId}/read`, { method: 'POST' }).catch(() => {});
      }
    } catch (e) {
      console.warn('Failed to load DMs:', e);
    }
  }

  async function loadRoomMessages(roomId) {
    messageStream.innerHTML = '<div style="text-align: center; color: var(--text-dim); padding: 1rem;">Loading room history...</div>';
    try {
      const res = await fetch(`/api/messages/room/${roomId}?limit=50&offset=0`);
      if (res.ok) {
        currentMessages = await res.json();
        renderMessageStream(currentMessages, 'room');
      }
    } catch (e) {
      console.warn('Failed to load room messages:', e);
    }
  }

  async function updateRoomParticipantsBadge(roomId) {
    try {
      const res = await fetch(`/api/rooms/${roomId}/participants`);
      if (res.ok) {
        const parts = await res.json();
        participantCountBadge.textContent = parts.length.toString();
      }
    } catch (e) {}
  }

  function renderMessageStream(messages, type) {
    messageStream.innerHTML = '';
    if (messages.length === 0) {
      messageStream.innerHTML = `<div style="text-align: center; color: var(--text-dim); margin: auto; font-size: 0.85rem;">
        No messages yet in this conversation. Send the first encrypted transmission.
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
    const row = document.createElement('div');
    row.className = `message-row ${isOutgoing ? 'outgoing' : 'incoming'}`;

    const timeStr = formatTime(msg.createdAt);
    const senderLabel = isOutgoing ? 'You' : (msg.senderDisplayName || 'Operative');

    let statusHtml = '';
    if (isOutgoing && msg.status) {
      const statusIcon = msg.status === 'READ' ? '✓✓' : (msg.status === 'DELIVERED' ? '✓' : '•');
      statusHtml = `<span class="message-status" title="${msg.status}">${statusIcon}</span>`;
    }

    row.innerHTML = `
      <div class="message-meta">
        <span class="sender-name">${escapeHtml(senderLabel)}</span>
        <span>•</span>
        <span>${timeStr}</span>
        ${statusHtml}
      </div>
      <div class="message-bubble">${escapeHtml(msg.content)}</div>
    `;

    messageStream.appendChild(row);
    if (autoScroll) scrollToBottom();
  }

  function scrollToBottom() {
    messageStream.scrollTop = messageStream.scrollHeight;
  }

  // 7. Sending Messages
  chatForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const text = chatInput.value.trim();
    if (!text || !currentChat) return;

    chatInput.value = '';

    if (currentChat.type === 'dm') {
      sendDirectMessage(currentChat.id, text);
    } else {
      sendRoomMessage(currentChat.id, text);
    }
  });

  function sendDirectMessage(recipientId, content) {
    const payload = {
      recipientId,
      content,
      clientMessageId: `cm-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`
    };

    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({
        type: 'SEND_DIRECT_MESSAGE',
        payload
      }));
    } else {
      // Fallback to REST
      fetch('/api/messages/direct', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      }).then(r => r.json()).then(dto => {
        appendMessageToStream(dto, true);
      }).catch(err => {
        showToast('Failed to transmit message', 'error');
      });
    }
  }

  function sendRoomMessage(roomId, content) {
    const payload = {
      roomId,
      content,
      clientMessageId: `rm-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`
    };

    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({
        type: 'SEND_ROOM_MESSAGE',
        payload
      }));
    } else {
      // Fallback to REST
      fetch('/api/messages/room', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      }).then(r => r.json()).then(dto => {
        appendMessageToStream(dto, true);
      }).catch(err => {
        showToast('Failed to post room message', 'error');
      });
    }
  }

  // Typing Notification
  chatInput.addEventListener('input', () => {
    if (!currentChat || !ws || ws.readyState !== WebSocket.OPEN) return;
    clearTimeout(typingTimeout);
    ws.send(JSON.stringify({
      type: 'TYPING_INDICATOR',
      payload: currentChat.type === 'room'
        ? { roomId: currentChat.id, typing: true }
        : { recipientId: currentChat.id, typing: true }
    }));
    typingTimeout = setTimeout(() => {
      ws.send(JSON.stringify({
        type: 'TYPING_INDICATOR',
        payload: currentChat.type === 'room'
          ? { roomId: currentChat.id, typing: false }
          : { recipientId: currentChat.id, typing: false }
      }));
    }, 2500);
  });

  // 8. Room Actions (Create, Join, Leave, Kick)
  createRoomForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('roomNameInput').value.trim();
    const description = document.getElementById('roomDescInput').value.trim();
    const capacity = parseInt(document.getElementById('roomCapacityInput').value, 10) || 50;
    const isPrivate = roomPrivateCheckbox.checked;
    const password = document.getElementById('roomPasswordInput').value;

    try {
      const res = await fetch('/api/rooms', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, description, capacity, isPrivate, password })
      });

      if (res.ok) {
        const room = await res.json();
        showToast(`Room created! Joining code: ${room.code}`, 'success');
        createRoomModal.classList.remove('active');
        createRoomForm.reset();
        loadRooms();
        selectConversation('room', room.id, room.name, false, room.code, true);
      } else {
        const err = await res.json();
        showToast(err.message || 'Failed to create room', 'error');
      }
    } catch (err) {
      showToast('Network error creating room', 'error');
    }
  });

  joinRoomForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const code = document.getElementById('joinRoomCodeInput').value.trim();
    const password = document.getElementById('joinRoomPasswordInput').value;
    await joinRoom(code, password);
  });

  async function joinRoom(code, password = null) {
    try {
      const res = await fetch('/api/rooms/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code, password })
      });

      if (res.ok) {
        const room = await res.json();
        showToast(`Joined room: ${room.name}`, 'success');
        joinRoomModal.classList.remove('active');
        joinRoomForm.reset();
        loadRooms();
        selectConversation('room', room.id, room.name, false, room.code, room.isOwner);
      } else {
        const err = await res.json();
        showToast(err.message || 'Failed to join room', 'error');
      }
    } catch (err) {
      showToast('Network error joining room', 'error');
    }
  }

  leaveRoomBtn.addEventListener('click', async () => {
    if (!currentChat || currentChat.type !== 'room') return;
    if (!confirm(`Are you sure you want to leave ${currentChat.name}?`)) return;

    try {
      const res = await fetch(`/api/rooms/${currentChat.id}/leave`, { method: 'POST' });
      if (res.ok) {
        showToast(`Left ${currentChat.name}`, 'info');
        currentChat = null;
        activeChatContainer.style.display = 'none';
        emptyChatPlaceholder.style.display = 'block';
        loadRooms();
      }
    } catch (e) {
      showToast('Error leaving room', 'error');
    }
  });

  // View Room Participants
  viewParticipantsBtn.addEventListener('click', async () => {
    if (!currentChat || currentChat.type !== 'room') return;
    try {
      const res = await fetch(`/api/rooms/${currentChat.id}/participants`);
      if (res.ok) {
        const parts = await res.json();
        renderParticipants(parts);
        participantsModal.classList.add('active');
      }
    } catch (e) {
      showToast('Failed to load participants', 'error');
    }
  });

  function renderParticipants(parts) {
    participantsList.innerHTML = '';
    parts.forEach(p => {
      const row = document.createElement('div');
      row.className = 'item-card';
      row.innerHTML = `
        <div class="item-info">
          <div class="avatar">${p.displayName.charAt(0).toUpperCase()}</div>
          <div class="item-details">
            <div class="item-name">${escapeHtml(p.displayName)} (${escapeHtml(p.username)})</div>
            <div class="item-sub">${p.role} • ${p.online ? 'Online' : 'Offline'}</div>
          </div>
        </div>
        ${currentChat.isOwner && p.userId !== currentUser.id
          ? `<button class="btn btn-danger btn-sm kick-btn" data-user-id="${p.userId}">Kick</button>`
          : ''}
      `;

      const kickBtn = row.querySelector('.kick-btn');
      if (kickBtn) {
        kickBtn.addEventListener('click', async () => {
          if (confirm(`Remove ${p.displayName} from this room?`)) {
            await kickParticipant(currentChat.id, p.userId);
          }
        });
      }

      participantsList.appendChild(row);
    });
  }

  async function kickParticipant(roomId, userId) {
    try {
      const res = await fetch(`/api/rooms/${roomId}/participants/${userId}`, { method: 'DELETE' });
      if (res.ok) {
        showToast('Participant removed', 'success');
        participantsModal.classList.remove('active');
        updateRoomParticipantsBadge(roomId);
      } else {
        const err = await res.json();
        showToast(err.message || 'Failed to remove', 'error');
      }
    } catch (e) {
      showToast('Error kicking member', 'error');
    }
  }

  // 9. Auth Submit
  authForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const isRegister = authTabRegister.classList.contains('active');
    const username = document.getElementById('authUsername').value.trim();
    const password = document.getElementById('authPassword').value;
    const displayName = document.getElementById('authDisplayName').value.trim();

    const endpoint = isRegister ? '/api/auth/register' : '/api/auth/login';
    const payload = isRegister ? { username, password, displayName } : { username, password };

    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (res.ok) {
        const data = await res.json();
        currentUser = data.user;
        authModal.classList.remove('active');
        authForm.reset();
        showToast(`Authenticated as ${currentUser.displayName}`, 'success');
        updateUserUI();
        initWebSocket();
        loadSidebarData();
      } else {
        const err = await res.json();
        showToast(err.message || 'Authentication failed', 'error');
      }
    } catch (err) {
      showToast('Connection failed', 'error');
    }
  });

  // Profile Modal & Update
  userProfileBtn.addEventListener('click', () => {
    if (!currentUser) return;
    document.getElementById('profileUsername').value = currentUser.username;
    document.getElementById('profileRole').value = currentUser.role;
    document.getElementById('profileDisplayName').value = currentUser.displayName || '';
    document.getElementById('profileBio').value = currentUser.bio || '';
    profileModal.classList.add('active');
  });

  profileForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const displayName = document.getElementById('profileDisplayName').value.trim();
    const bio = document.getElementById('profileBio').value.trim();

    try {
      const res = await fetch('/api/users/profile', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ displayName, bio })
      });

      if (res.ok) {
        currentUser = await res.json();
        updateUserUI();
        profileModal.classList.remove('active');
        showToast('Profile updated', 'success');
      }
    } catch (e) {
      showToast('Error updating profile', 'error');
    }
  });

  logoutBtn.addEventListener('click', async () => {
    try {
      await fetch('/api/auth/logout', { method: 'POST' });
    } catch (e) {}
    currentUser = null;
    if (ws) ws.close();
    location.reload();
  });

  // 10. Diagnostics Runner
  diagnosticBtn.addEventListener('click', runDiagnosticsProbe);
  rerunDiagnosticBtn.addEventListener('click', runDiagnosticsProbe);

  async function runDiagnosticsProbe() {
    diagnosticModal.classList.add('active');
    diagnosticContent.innerHTML = 'Running connectivity probes on local interface...';
    try {
      const res = await fetch('/api/admin/diagnostics');
      if (res.ok) {
        const diag = await res.json();
        diagnosticContent.innerHTML = `
          <div style="margin-bottom: 8px;"><strong>Status:</strong> ${diag.serverReachableLocally ? '<span style="color:var(--neon-green)">LOCAL SERVER REACHABLE</span>' : '<span style="color:var(--neon-red)">PORT UNREACHABLE</span>'}</div>
          <div style="margin-bottom: 8px;"><strong>Host Reachable IP:</strong> <span style="color:var(--neon-cyan)">${diag.primaryLocalIp}</span></div>
          <div style="margin-bottom: 8px;"><strong>Bound Port:</strong> ${diag.port}</div>
          <div style="margin-bottom: 8px;"><strong>Client Access URL:</strong> <a href="${diag.accessUrl}" target="_blank" style="color:var(--neon-blue)">${diag.accessUrl}</a></div>
          <div style="margin-bottom: 8px;"><strong>Operating Mode:</strong> <span style="color:var(--neon-yellow)">${diag.operatingMode}</span></div>
          <div style="margin-bottom: 8px;"><strong>Internet Status:</strong> ${diag.internetReachable ? 'Connected (Optional)' : '<span style="color:var(--neon-cyan)">Air-Gapped Offline Mode (Active)</span>'}</div>
          <div style="padding: 8px; background: var(--bg-surface); border-radius: 4px; margin-top: 10px;">${escapeHtml(diag.summary)}</div>
        `;
      } else {
        diagnosticContent.innerHTML = '<span style="color: var(--neon-red)">Diagnostic API requires Host Administrator privileges.</span>';
      }
    } catch (e) {
      diagnosticContent.innerHTML = '<span style="color: var(--neon-red)">Probe failed to execute.</span>';
    }
  }

  // Tab switching
  tabDmBtn.addEventListener('click', () => {
    tabDmBtn.classList.add('active');
    tabRoomsBtn.classList.remove('active');
    sidebarDmContent.style.display = 'block';
    sidebarRoomsContent.style.display = 'none';
  });

  tabRoomsBtn.addEventListener('click', () => {
    tabRoomsBtn.classList.add('active');
    tabDmBtn.classList.remove('active');
    sidebarDmContent.style.display = 'none';
    sidebarRoomsContent.style.display = 'block';
    loadRooms();
  });

  refreshUsersBtn.addEventListener('click', loadUsers);
  authTabLogin.addEventListener('click', () => openAuthModal(false));
  authTabRegister.addEventListener('click', () => openAuthModal(true));
  loginOpenBtn.addEventListener('click', () => openAuthModal(false));

  closeAuthModalBtn.addEventListener('click', () => authModal.classList.remove('active'));
  closeCreateRoomModalBtn.addEventListener('click', () => createRoomModal.classList.remove('active'));
  cancelCreateRoomBtn.addEventListener('click', () => createRoomModal.classList.remove('active'));
  openCreateRoomBtn.addEventListener('click', () => createRoomModal.classList.add('active'));
  quickCreateRoomBtn.addEventListener('click', () => createRoomModal.classList.add('active'));

  closeJoinRoomModalBtn.addEventListener('click', () => joinRoomModal.classList.remove('active'));
  cancelJoinRoomBtn.addEventListener('click', () => joinRoomModal.classList.remove('active'));
  openJoinRoomBtn.addEventListener('click', () => joinRoomModal.classList.add('active'));
  quickJoinRoomBtn.addEventListener('click', () => joinRoomModal.classList.add('active'));

  closeProfileModalBtn.addEventListener('click', () => profileModal.classList.remove('active'));
  closeParticipantsModalBtn.addEventListener('click', () => participantsModal.classList.remove('active'));
  closeDiagnosticModalBtn.addEventListener('click', () => diagnosticModal.classList.remove('active'));

  roomPrivateCheckbox.addEventListener('change', () => {
    roomPasswordGroup.style.display = roomPrivateCheckbox.checked ? 'flex' : 'none';
  });

  if (mobileMenuBtn) {
    mobileMenuBtn.addEventListener('click', () => {
      appSidebar.classList.toggle('open');
    });
  }

  // Utilities
  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function formatTime(isoStr) {
    if (!isoStr) return '';
    const d = new Date(isoStr);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  function highlightUserCard(userId) {
    const card = document.querySelector(`.user-card[data-user-id="${userId}"]`);
    if (card) {
      card.style.borderColor = 'var(--neon-cyan)';
      setTimeout(() => { card.style.borderColor = ''; }, 3000);
    }
  }

  // Bootstrap
  injectIcons();
  checkAuth();
})();
