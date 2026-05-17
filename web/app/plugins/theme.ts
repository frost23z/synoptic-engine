import { themeIcons } from '../utils/theme'

export default defineNuxtPlugin({
    enforce: 'post',
    setup() {
        const appConfig = useAppConfig()

        if (import.meta.client) {
            const primary = localStorage.getItem('nuxt-ui-primary')
            if (primary) appConfig.ui.colors.primary = primary

            const neutral = localStorage.getItem('nuxt-ui-neutral')
            if (neutral) appConfig.ui.colors.neutral = neutral

            const iconSet = localStorage.getItem('nuxt-ui-icons') || 'lucide'
            appConfig.ui.icons = themeIcons[
                iconSet as keyof typeof themeIcons
            ] as unknown as typeof appConfig.ui.icons
        }

        if (import.meta.server) {
            // Inline scripts restore persisted theme on first paint to prevent FOUC.
            // Each script targets the <style> element injected by useTheme via useHead.
            useHead({
                script: [
                    {
                        // Swap primary/neutral CSS variables in the Nuxt UI style block
                        innerHTML: `(function(){
  var p=localStorage.getItem('nuxt-ui-primary'),n=localStorage.getItem('nuxt-ui-neutral');
  if(!p&&!n)return;
  function swap(el){
    var t=el.textContent;
    if(p&&p!=='black')t=t.replace(/(--ui-color-primary-\\d{2,3}:\\s*var\\(--color-)${appConfig.ui.colors.primary}(-\\d{2,3}.*?\\))/g,'$1'+p+'$2');
    if(n)t=t.replace(/(--ui-color-neutral-\\d{2,3}:\\s*var\\(--color-)${appConfig.ui.colors.neutral}(-\\d{2,3}.*?\\))/g,'$1'+n+'$2');
    el.textContent=t;
  }
  var el=document.querySelector('style#nuxt-ui-colors');
  if(el){swap(el);}else{var obs=new MutationObserver(function(ms){for(var i=0;i<ms.length;i++){for(var j=0;j<ms[i].addedNodes.length;j++){var nd=ms[i].addedNodes[j];if(nd.id==='nuxt-ui-colors'){swap(nd);obs.disconnect();return;}}}});obs.observe(document.head,{childList:true});}
})();`,
                        type: 'text/javascript',
                        tagPriority: -1,
                    },
                    {
                        innerHTML: `(function(){
  var r=localStorage.getItem('nuxt-ui-radius');
  if(r){var el=document.querySelector('style#nuxt-ui-radius');if(el)el.textContent=':root{--ui-radius:'+r+'rem}';}
})();`,
                        type: 'text/javascript',
                        tagPriority: -1,
                    },
                    {
                        innerHTML: `(function(){
  var el=document.querySelector('style#nuxt-ui-black-as-primary');
  if(!el)return;
  el.textContent=localStorage.getItem('nuxt-ui-black-as-primary')==='true'
    ?':root{--ui-primary:black}.dark{--ui-primary:white}'
    :'';
})();`,
                        type: 'text/javascript',
                        tagPriority: -1,
                    },
                    {
                        innerHTML: `(function(){
  var f=localStorage.getItem('nuxt-ui-font');
  if(!f)return;
  var el=document.querySelector('style#nuxt-ui-font');
  if(el)el.textContent=":root{--font-sans:'"+f+"',sans-serif}";
  if(f!=='DM Sans'){
    var lnk=document.createElement('link');
    lnk.rel='stylesheet';
    lnk.href='https://fonts.googleapis.com/css2?family='+encodeURIComponent(f)+':wght@400;500;600;700&display=swap';
    lnk.id='font-'+f.toLowerCase().replace(/\\s+/g,'-');
    document.head.appendChild(lnk);
  }
})();`,
                        type: 'text/javascript',
                        tagPriority: -1,
                    },
                ],
            })
        }
    },
})
