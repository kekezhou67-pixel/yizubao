let router
export function configureAuthNavigation(value) { router = value }
export function safeAuthRedirect(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || Array.from(value).some(c => c.charCodeAt(0) < 32 || c.charCodeAt(0) === 92)) return '/'
  const path = value.split(/[?#]/)[0]
  if (['/login','/register'].includes(path)) return '/'
  if (router) { const resolved = router.resolve(value); if (!resolved.matched.length || resolved.meta.authPage) return '/' }
  return value
}
export function openAuthPage(to) {
  const current = router?.currentRoute.value
  if (!router || current?.meta.authPage) return
  return router.push({ path:'/login', query:{ redirect:safeAuthRedirect(to || current?.fullPath || '/') } })
}
