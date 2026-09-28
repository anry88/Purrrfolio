import "../index.css";
import {registerRoot, Composition} from "remotion";
import {DuplicateMondayVideo, DuplicateMondayThumbnailPair, DuplicateMondayThumbnailPoints} from "./DuplicateMonday";

const MondayRoot: React.FC = () => <>
  <Composition id="PurrrfolioDuplicateMonday20260928" component={DuplicateMondayVideo} durationInFrames={990} fps={30} width={1080} height={1920} />
  <Composition id="DuplicateMondayThumbnailPair" component={DuplicateMondayThumbnailPair} durationInFrames={1} fps={30} width={1080} height={1920} />
  <Composition id="DuplicateMondayThumbnailPoints" component={DuplicateMondayThumbnailPoints} durationInFrames={1} fps={30} width={1080} height={1920} />
</>;

registerRoot(MondayRoot);
