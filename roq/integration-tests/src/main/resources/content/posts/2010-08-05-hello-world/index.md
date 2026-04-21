# Hello!

Here are the links: {page.file('hello.pdf')} and {page.file('./hello.pdf')}

and an images: {site.image('hello.png')}, {site.image('hello.foo.png')} and {page.image('hello-page.png')} and  {page.image('./hello-page.png')}

page by path: {site.page('élo you$@.html').orEmpty().url}
document by path: {site.document('posts/markdown-post-k8s.md').url}