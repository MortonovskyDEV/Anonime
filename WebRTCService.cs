using System;
using System.Threading.Tasks;
using WebRTC.Signalling;
using WebRTC.Media;

namespace GothMessenger.Services
{
    public class WebRTCService : IDisposable
    {
        private RTCPeerConnection _peerConnection;
        private MediaStream _localStream;
        private MediaStream _remoteStream;
        private readonly WebSocketSignaller _signaller;
        
        public event EventHandler<MediaStream> LocalStreamAvailable;
        public event EventHandler<MediaStream> RemoteStreamAvailable;
        public event EventHandler<string> ConnectionStateChanged;
        
        public WebRTCService(string serverUrl, string token)
        {
            _signaller = new WebSocketSignaller($"{serverUrl}/ws", token);
            _signaller.MessageReceived += OnSignallerMessage;
            InitializePeerConnection();
        }
        
        private void InitializePeerConnection()
        {
            var configuration = new RTCConfiguration
            {
                IceServers = new[]
                {
                    new RTCIceServer
                    {
                        Urls = new[] { "stun:stun.l.google.com:19302" }
                    }
                }
            };
            
            _peerConnection = new RTCPeerConnection(configuration);
            
            _peerConnection.OnIceCandidate += candidate =>
            {
                _signaller.SendIceCandidate(candidate);
            };
            
            _peerConnection.OnTrack += e =>
            {
                _remoteStream = e.Streams[0];
                RemoteStreamAvailable?.Invoke(this, _remoteStream);
            };
            
            _peerConnection.OnConnectionStateChange += state =>
            {
                ConnectionStateChanged?.Invoke(this, state.ToString());
            };
        }
        
        public async Task StartLocalStream(bool withVideo = true)
        {
            _localStream = await MediaStream.GetUserMedia(new MediaStreamConstraints
            {
                Audio = true,
                Video = withVideo ? new MediaTrackConstraints
                {
                    Width = 1280,
                    Height = 720,
                    FrameRate = 30
                } : null
            });
            
            foreach (var track in _localStream.GetTracks())
            {
                _peerConnection.AddTrack(track, _localStream);
            }
            
            LocalStreamAvailable?.Invoke(this, _localStream);
        }
        
        public async Task StartCall(long chatId, bool isVideoCall)
        {
            await StartLocalStream(isVideoCall);
            
            var offer = await _peerConnection.CreateOffer();
            await _peerConnection.SetLocalDescription(offer);
            
            await _signaller.SendOffer(chatId, offer);
        }
        
        public async Task AnswerCall(RTCSessionDescription offer)
        {
            await _peerConnection.SetRemoteDescription(offer);
            await StartLocalStream(offer.Type == "video");
            
            var answer = await _peerConnection.CreateAnswer();
            await _peerConnection.SetLocalDescription(answer);
            
            await _signaller.SendAnswer(answer);
        }
        
        private async void OnSignallerMessage(object sender, SignallerMessage message)
        {
            switch (message.Type)
            {
                case "offer":
                    await AnswerCall(message.Offer);
                    break;
                    
                case "answer":
                    await _peerConnection.SetRemoteDescription(message.Answer);
                    break;
                    
                case "ice-candidate":
                    await _peerConnection.AddIceCandidate(message.Candidate);
                    break;
            }
        }
        
        public void ToggleLocalVideo(bool enable)
        {
            var videoTrack = _localStream?.GetVideoTracks().FirstOrDefault();
            if (videoTrack != null)
            {
                videoTrack.Enabled = enable;
            }
        }
        
        public void ToggleLocalAudio(bool enable)
        {
            var audioTrack = _localStream?.GetAudioTracks().FirstOrDefault();
            if (audioTrack != null)
            {
                audioTrack.Enabled = enable;
            }
        }
        
        public async Task EndCall()
        {
            _peerConnection.Close();
            _localStream?.Dispose();
            _remoteStream?.Dispose();
            await _signaller.Disconnect();
        }
        
        public void Dispose()
        {
            _peerConnection?.Dispose();
            _localStream?.Dispose();
            _remoteStream?.Dispose();
            _signaller?.Dispose();
        }
    }
}