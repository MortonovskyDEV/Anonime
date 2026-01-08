// Добавьте WebRTC signalling endpoint
router.post('/api/webrtc/signal', async (request, env) => {
  const { chatId, userId, signal, type } = await request.json();
  
  // Пересылка сигналов через WebSocket
  const participantWs = activeConnections.get(userId);
  if (participantWs) {
    participantWs.send(JSON.stringify({
      type: 'webrtc_signal',
      signal,
      signalType: type
    }));
  }
  
  return new Response(JSON.stringify({ success: true }), {
    headers: { 'Content-Type': 'application/json' }
  });
});