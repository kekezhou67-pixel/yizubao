<template><div class="business-layout"><RouterView v-slot="{ Component, route }"><component :is="Component" :key="route.path" class="page-enter" /></RouterView></div></template>
<style scoped>.business-layout{min-height:calc(100dvh - var(--header-height));background:var(--jade-paper)}</style>
