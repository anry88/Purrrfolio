import { AbsoluteFill, Audio, Img, interpolate, staticFile, useCurrentFrame } from "remotion";

const ink = "#173c32";
const cream = "#fff9e9";
const mint = "#d5efcf";
const gold = "#f3bf68";
const card = staticFile("cards/yarn-keeper.png");

const opacityAt = (frame: number, start: number, end: number) =>
  interpolate(frame, [start, start + 12, end - 12, end], [0, 1, 1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

const Stage: React.FC<{children: React.ReactNode}> = ({children}) => (
  <AbsoluteFill style={{background: "radial-gradient(circle at 50% 30%, #fffdf1 0%, #e5f2d8 48%, #97c8a1 100%)", color: ink, fontFamily: '"Avenir Next", Arial, sans-serif'}}>
    <div style={{position: "absolute", top: 102, left: 78, fontSize: 32, fontWeight: 900, letterSpacing: 5}}>PURRRFOLIO</div>
    <div style={{position: "absolute", left: 78, right: 78, bottom: 112, height: 5, background: ink, opacity: 0.25, borderRadius: 6}} />
    {children}
  </AbsoluteFill>
);

const Card: React.FC<{width: number; top: number; left: number; rotate?: number}> = ({width, top, left, rotate = 0}) => (
  <Img src={card} style={{position: "absolute", width, height: width * 1.5, top, left, borderRadius: 30, transform: `rotate(${rotate}deg)`, boxShadow: "0 30px 65px #193c3145"}} />
);

const Heading: React.FC<{eyebrow: string; title: React.ReactNode}> = ({eyebrow, title}) => (
  <div style={{position: "absolute", top: 225, left: 78, right: 78}}>
    <div style={{fontSize: 31, letterSpacing: 4, fontWeight: 900, color: "#59885c"}}>{eyebrow}</div>
    <div style={{fontSize: 91, fontWeight: 950, lineHeight: 1.02, marginTop: 16}}>{title}</div>
  </div>
);

const Bottom: React.FC<{children: React.ReactNode}> = ({children}) => (
  <div style={{position: "absolute", left: 78, right: 78, bottom: 185, background: ink, color: cream, borderRadius: 28, padding: "27px 34px", fontSize: 49, fontWeight: 800, textAlign: "center", lineHeight: 1.15}}>{children}</div>
);

const Hook: React.FC = () => (
  <Stage>
    <Heading eyebrow="A SUNDAY PAWCAST IDEA" title={<>Same cat<br/>twice?</>} />
    <Card width={520} top={560} left={122} rotate={-9} />
    <Card width={520} top={635} left={446} rotate={9} />
    <Bottom>A duplicate can still matter.</Bottom>
  </Stage>
);

const Hero: React.FC = () => {
  const f = useCurrentFrame();
  const scale = interpolate(f, [180, 390], [0.98, 1.03], {extrapolateLeft: "clamp", extrapolateRight: "clamp"});
  return <Stage>
    <Heading eyebrow="MEET THE CARD" title="Yarn Keeper" />
    <div style={{position: "absolute", top: 525, left: 193, transform: `scale(${scale})`, transformOrigin: "center"}}>
      <Img src={card} style={{width: 694, height: 1041, borderRadius: 34, boxShadow: "0 35px 75px #193c314d"}} />
    </div>
    <Bottom>Uncommon · Cozy Home</Bottom>
  </Stage>;
};

const Spare: React.FC = () => (
  <Stage>
    <Heading eyebrow="THE CRAFTING RULE" title={<>Keep one.<br/>Use the spare.</>} />
    <Card width={398} top={676} left={83} rotate={-5} />
    <Card width={398} top={676} left={598} rotate={5} />
    <div style={{position: "absolute", top: 1320, left: 102, width: 350, background: mint, border: `5px solid ${ink}`, borderRadius: 60, padding: "20px 0", fontSize: 41, fontWeight: 900, textAlign: "center"}}>KEEP</div>
    <div style={{position: "absolute", top: 1320, left: 628, width: 350, background: gold, border: `5px solid ${ink}`, borderRadius: 60, padding: "20px 0", fontSize: 41, fontWeight: 900, textAlign: "center"}}>CRAFT</div>
    <Bottom>Only spare copies can be melted.</Bottom>
  </Stage>
);

const Points: React.FC = () => (
  <Stage>
    <Heading eyebrow="UNCOMMON DUPLICATE" title={<>Spare card<br/>→ 2 points</>} />
    <Card width={475} top={650} left={302} />
    <div style={{position: "absolute", top: 1380, left: 128, right: 128, background: cream, borderRadius: 42, border: `5px solid ${ink}`, padding: 30, textAlign: "center", fontSize: 63, fontWeight: 900}}>2 / 15</div>
    <Bottom>Rarity sets the point value.</Bottom>
  </Stage>
);

const End: React.FC = () => (
  <Stage>
    <Heading eyebrow="KEEP COLLECTING" title={<>15 points<br/>= 1 pack</>} />
    <div style={{position: "absolute", top: 700, left: 230, width: 620, height: 540, background: "linear-gradient(145deg, #f8d694, #edaf5d)", border: `9px solid ${ink}`, borderRadius: 58, boxShadow: "0 35px 75px #193c3144", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", transform: "rotate(-4deg)"}}>
      <div style={{fontSize: 150, lineHeight: 1}}>✦</div>
      <div style={{fontSize: 66, fontWeight: 950, letterSpacing: 3}}>CAT PACK</div>
      <div style={{fontSize: 30, fontWeight: 850, letterSpacing: 5, marginTop: 12}}>PURRRFOLIO</div>
    </div>
    <div style={{position: "absolute", top: 1300, left: 110, right: 110, fontSize: 58, fontWeight: 900, textAlign: "center"}}>Which duplicate would you craft?</div>
    <Bottom>Find @PurrrfolioBot in Telegram</Bottom>
  </Stage>
);

export const DuplicateMondayVideo: React.FC = () => {
  const frame = useCurrentFrame();
  const scenes = [
    {start: 0, end: 180, component: <Hook />},
    {start: 180, end: 390, component: <Hero />},
    {start: 390, end: 630, component: <Spare />},
    {start: 630, end: 810, component: <Points />},
    {start: 810, end: 990, component: <End />},
  ];
  return <AbsoluteFill>
    <Audio src={staticFile("audio/generated/youtube-short-20260928-monday-01.wav")} />
    {scenes.map(({start, end, component}, i) => <AbsoluteFill key={i} style={{opacity: i === 0 ? (frame < end - 12 ? 1 : opacityAt(frame, -12, end)) : i === scenes.length - 1 ? interpolate(frame, [start, start + 12], [0, 1], {extrapolateLeft: "clamp", extrapolateRight: "clamp"}) : opacityAt(frame, start, end)}}>{component}</AbsoluteFill>)}
  </AbsoluteFill>;
};

export const DuplicateMondayThumbnailPair: React.FC = () => (
  <Stage>
    <div style={{position: "absolute", top: 360, left: 90, right: 90, fontSize: 110, fontWeight: 950, lineHeight: 1.02, textAlign: "center"}}>SAME CAT<br/>TWICE?</div>
    <Card width={520} top={745} left={130} rotate={-10} />
    <Card width={520} top={760} left={445} rotate={10} />
  </Stage>
);

export const DuplicateMondayThumbnailPoints: React.FC = () => (
  <Stage>
    <div style={{position: "absolute", top: 370, left: 100, right: 100, fontSize: 106, fontWeight: 950, lineHeight: 1.02, textAlign: "center"}}>DUPLICATES<br/>BECOME PACKS</div>
    <Card width={650} top={720} left={215} rotate={-4} />
    <div style={{position: "absolute", top: 1230, left: 530, width: 400, background: gold, border: `6px solid ${ink}`, borderRadius: 50, padding: "28px 15px", fontSize: 66, fontWeight: 950, textAlign: "center"}}>15 PTS</div>
  </Stage>
);
