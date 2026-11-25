import { beforeEach, describe, expect, it } from 'vitest'
import router from '../router'
import { clearSession, setSession } from '../auth'

describe('Anmelde-Sperre', () => {
  beforeEach(() => clearSession())

  it('schickt Gäste zur Anmeldung', async () => {
    await router.push('/rangliste')
    expect(router.currentRoute.value.name).toBe('anmelden')
    expect(router.currentRoute.value.query.redirect).toBe('/rangliste')
  })

  it('lässt angemeldete Spieler durch und hält sie von /anmelden fern', async () => {
    setSession('token-123', 'spieler')
    await router.push('/rangliste')
    expect(router.currentRoute.value.name).toBe('rangliste')

    await router.push('/anmelden')
    expect(router.currentRoute.value.name).toBe('home')
  })
})
