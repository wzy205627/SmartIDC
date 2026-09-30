/**
 * 工业级动环告警 Web Audio 合成器 (零外部音频文件依赖)
 * 采用双频交替调制与指数增益衰减算法，防止扬声器爆破声与跨域音频加载失败
 */

class AlarmAudioPlayer {
  constructor() {
    this.ctx = null
    this.muted = localStorage.getItem('smartidc_screen_muted') === 'true'
    this.isUnlocked = false
    this.initUserGestureListener()
  }

  /**
   * 监听用户手势交互以安全解锁现代浏览器的 Web Audio Context
   */
  initUserGestureListener() {
    const unlock = () => {
      this.ensureContext()
      if (this.ctx && this.ctx.state === 'suspended') {
        this.ctx.resume().then(() => {
          this.isUnlocked = true
        })
      } else if (this.ctx && this.ctx.state === 'running') {
        this.isUnlocked = true
      }
      window.removeEventListener('click', unlock)
      window.removeEventListener('keydown', unlock)
    }
    window.addEventListener('click', unlock)
    window.addEventListener('keydown', unlock)
  }

  ensureContext() {
    if (!this.ctx) {
      const AudioContextClass = window.AudioContext || window.webkitAudioContext
      if (AudioContextClass) {
        this.ctx = new AudioContextClass()
      }
    }
  }

  get isMuted() {
    return this.muted
  }

  setMuted(val) {
    this.muted = !!val
    localStorage.setItem('smartidc_screen_muted', this.muted ? 'true' : 'false')
    return this.muted
  }

  toggleMute() {
    return this.setMuted(!this.muted)
  }

  /**
   * 触发严重警报蜂鸣声 (CRITICAL - 工业级双频调制警报)
   */
  playCritical() {
    if (this.muted) return

    this.ensureContext()
    if (!this.ctx) return

    if (this.ctx.state === 'suspended') {
      this.ctx.resume().then(() => this._executeCriticalSound())
    } else {
      this._executeCriticalSound()
    }
  }

  _executeCriticalSound() {
    try {
      const now = this.ctx.currentTime
      const osc = this.ctx.createOscillator()
      const gain = this.ctx.createGain()

      osc.type = 'sawtooth' // 工业警报常用的丰富谐波

      // 双频交替调制 (880Hz 与 587Hz 快速交替)
      osc.frequency.setValueAtTime(880, now)
      osc.frequency.setValueAtTime(587, now + 0.12)
      osc.frequency.setValueAtTime(880, now + 0.24)
      osc.frequency.setValueAtTime(587, now + 0.36)
      osc.frequency.setValueAtTime(880, now + 0.48)

      // 平滑音量曲线防爆音
      gain.gain.setValueAtTime(0.001, now)
      gain.gain.exponentialRampToValueAtTime(0.18, now + 0.03)
      gain.gain.setValueAtTime(0.18, now + 0.50)
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.60)

      osc.connect(gain)
      gain.connect(this.ctx.destination)

      osc.start(now)
      osc.stop(now + 0.62)
    } catch (e) {
      console.warn('[AlarmAudio] 播放严重告警音效失败:', e)
    }
  }

  /**
   * 触发一般预警音 (WARNING - 清脆双音提示)
   */
  playWarning() {
    if (this.muted) return

    this.ensureContext()
    if (!this.ctx) return

    if (this.ctx.state === 'suspended') {
      this.ctx.resume().then(() => this._executeWarningSound())
    } else {
      this._executeWarningSound()
    }
  }

  _executeWarningSound() {
    try {
      const now = this.ctx.currentTime
      const osc = this.ctx.createOscillator()
      const gain = this.ctx.createGain()

      osc.type = 'sine'
      osc.frequency.setValueAtTime(659, now) // E5
      osc.frequency.setValueAtTime(880, now + 0.15) // A5

      gain.gain.setValueAtTime(0.001, now)
      gain.gain.exponentialRampToValueAtTime(0.12, now + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.35)

      osc.connect(gain)
      gain.connect(this.ctx.destination)

      osc.start(now)
      osc.stop(now + 0.36)
    } catch (e) {
      console.warn('[AlarmAudio] 播放预警提示音失败:', e)
    }
  }

  /**
   * 声音测试/试听 (用于用户点击开关时确认音响正常)
   */
  testAudio() {
    this.playWarning()
  }
}

export const alarmAudio = new AlarmAudioPlayer()
