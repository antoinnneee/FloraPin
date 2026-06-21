import { DeviceTokensService } from './device-tokens.service';
import {
  FcmMessaging,
  FcmMulticastResponse,
  FcmPushSender,
} from './fcm-push.sender';

describe('FcmPushSender', () => {
  function build(response: FcmMulticastResponse) {
    const sent: { tokens: string[]; data: Record<string, string> }[] = [];
    const messaging: FcmMessaging = {
      sendEachForMulticast: async (message) => {
        sent.push(message);
        return response;
      },
    };
    const unregister = jest.fn();
    const devices = { unregister } as unknown as DeviceTokensService;
    return { sender: new FcmPushSender(messaging, devices), sent, unregister };
  }

  it('sérialise type + payload en chaînes', async () => {
    const { sender, sent } = build({ responses: [{ success: true }] });

    await sender.send(['t1'], {
      type: 'flower_shared',
      data: { shareId: 'x', count: 3, flowerId: null },
    });

    expect(sent[0].tokens).toEqual(['t1']);
    expect(sent[0].data).toEqual({
      type: 'flower_shared',
      shareId: 'x',
      count: '3',
      flowerId: '',
    });
  });

  it('purge les jetons invalides signalés par FCM', async () => {
    const { sender, unregister } = build({
      responses: [
        { success: true },
        {
          success: false,
          error: { code: 'messaging/registration-token-not-registered' },
        },
      ],
    });

    await sender.send(['good', 'bad'], { type: 'x', data: {} });

    expect(unregister).toHaveBeenCalledTimes(1);
    expect(unregister).toHaveBeenCalledWith('bad');
  });

  it('ne purge pas sur une erreur transitoire', async () => {
    const { sender, unregister } = build({
      responses: [
        { success: false, error: { code: 'messaging/internal-error' } },
      ],
    });

    await sender.send(['t1'], { type: 'x', data: {} });

    expect(unregister).not.toHaveBeenCalled();
  });

  it('ne fait rien sans jeton', async () => {
    const { sender, sent } = build({ responses: [] });
    await sender.send([], { type: 'x', data: {} });
    expect(sent).toHaveLength(0);
  });
});
