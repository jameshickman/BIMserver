# BIMserver behind an ngrok tunnel

Runs a development BIMserver from this checkout and publishes it on an ngrok URL, so it can be used like a server on the internet.

```sh
cp ngrok/bimserver-ngrok.env.example ngrok/bimserver-ngrok.env   # optional
ngrok/start-bimserver.sh            # add --build to rebuild BIMserver first
```

The script prints the public URL when the server is ready. Ctrl-C stops the tunnel and the server.

## What it does

1. Starts `org.bimserver.PublicBimServerStarter` on `127.0.0.1` only, so it is not reachable from your network except through the tunnel.
2. On the first start, sets up the server with your admin account and installs the standard plugin bundles (IFC, binary serializers, IfcOpenShell, mergers) from Maven Central. Data is kept in `BimServerJar/home-public/`.
3. Opens the tunnel only after setup has finished, so nobody else can reach the setup page and claim the admin account.
4. Sets the server's site address to the public URL, so links the server generates point there.

If an ngrok agent is already running (its API on `127.0.0.1:4040`), the tunnel is added to that agent and removed again on exit; free ngrok accounts allow only one agent session. Otherwise the script starts its own agent.

## Admin password

There is no default `admin`/`admin` account. The starter refuses to run without `BIMSERVER_ADMIN_PASSWORD` of at least 16 characters. If you don't set one, the script generates a random password and saves it to `ngrok/bimserver-ngrok.env` (git-ignored, readable only by you).

The password is used to create the account on the first start only. To change it afterwards, use the BIMserver UI, then update `bimserver-ngrok.env`, because the script logs in with it.

## Settings (`ngrok/bimserver-ngrok.env`)

| Variable | Default | |
|---|---|---|
| `BIMSERVER_ADMIN_USERNAME` | `admin@bimserver.org` | Must be an e-mail address |
| `BIMSERVER_ADMIN_PASSWORD` | generated | At least 16 characters |
| `NGROK_DOMAIN` | none | Your reserved ngrok domain. Without it the URL changes on every start |
| `BIMSERVER_PORT` | `8080` | Local port |
| `BIMSERVER_HOME` | `home-public` | Data directory, relative to `BimServerJar/` |
| `BIMSERVER_XMX` | `4g` | Java heap |
| `NGROK_API` | `http://127.0.0.1:4040` | API of an already running ngrok agent |
| `NGROK_TUNNEL_NAME` | `bimserver` | Name of the tunnel added to a running agent |

## Notes

- On free ngrok domains, browsers see an ngrok warning page once before reaching BIMserver. API clients that send a non-browser `User-Agent`, or the `ngrok-skip-browser-warning` header, are not affected.
- Logs are in `BimServerJar/home-public/`: `server-console.log`, `ngrok.log` and `logs/bimserver.log`.
- Anyone with the URL can reach the login page and the JSON API. Create separate accounts for other people instead of sharing the admin login.
